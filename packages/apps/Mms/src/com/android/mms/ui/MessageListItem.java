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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract.Profile;
import android.provider.Telephony.Sms;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.LineHeightSpan;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.provider.Telephony;
import android.provider.Telephony.Mms;

import com.android.mms.LogTag;
import com.android.mms.MmsApp;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.WorkingMessage;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.transaction.Transaction;
import com.android.mms.transaction.TransactionBundle;
import com.android.mms.transaction.TransactionService;
import com.android.mms.util.DownloadManager;
import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.ThumbnailManager.ImageLoaded;
import com.android.mms.util.MmsContentType;
import com.google.android.mms.pdu.PduHeaders;

/// M: @{
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.MailTo;
import android.provider.Browser;
import android.text.style.LeadingMarginSpan;
import android.text.Spannable;
import android.widget.CheckBox;
import android.widget.Toast;

import com.android.internal.telephony.PhoneConstants;
import com.android.mms.MmsConfig;
import com.android.mms.model.FileAttachmentModel;
import com.android.mms.model.VCardModel;
import com.android.mms.util.VCardUtils;
import com.google.android.mms.util.SqliteWrapper;
import com.android.mms.util.FeatureOption;
import com.android.mms.util.MmsLog;
import com.mediatek.drm.OmaDrmClient;
import com.mediatek.drm.OmaDrmUiUtils;
import com.android.mms.util.MessageResource;
import com.mediatek.ipmsg.util.IpMessageUtils;
import com.mediatek.mms.ext.IMmsCancelDownloadExt;
import com.mediatek.mms.ext.IMmsComposeExt;
import com.mediatek.mms.ext.IMmsMessageListItemExt;
import com.mediatek.mms.ext.IMmsUtilsExt;
import com.mediatek.mms.ext.DefaultMmsMessageListItemExt;
import com.mediatek.storage.StorageManagerEx;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
/// @}


/// M: add for ip message
import android.app.Activity;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.provider.MediaStore.Video.Thumbnails;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.ProgressBar;

import com.mediatek.mms.ipmessage.INotificationsListener;
import com.mediatek.mms.ipmessage.message.IpAttachMessage;
import com.mediatek.mms.ipmessage.message.IpImageMessage;
import com.mediatek.mms.ipmessage.message.IpMessage;
import com.mediatek.mms.ipmessage.IpMessageConsts;
import com.mediatek.mms.ipmessage.IpMessageConsts.DownloadAttachStatus;
import com.mediatek.mms.ipmessage.IpMessageConsts.FeatureId;
import com.mediatek.mms.ipmessage.IpMessageConsts.IpMessageStatus;
import com.mediatek.mms.ipmessage.IpMessageConsts.IpMessageType;
import com.mediatek.mms.ipmessage.IpMessageConsts.RemoteActivities;
import com.mediatek.mms.ipmessage.message.IpTextMessage;
import com.mediatek.mms.ipmessage.message.IpVCalendarMessage;
import com.mediatek.mms.ipmessage.message.IpVCardMessage;
import com.mediatek.mms.ipmessage.message.IpVideoMessage;
import com.mediatek.mms.ipmessage.message.IpVoiceMessage;

/// M: @{

//add for attachment enhance


import java.io.FileInputStream;
import java.io.FileOutputStream;

import android.content.ContentResolver;
import android.content.res.Configuration;
import android.os.Environment;

import com.android.mms.MmsPluginManager;
import com.mediatek.mms.ext.IMmsAttachmentEnhanceExt;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.MmsException;
import com.mediatek.common.MPlugin;

import android.graphics.drawable.Drawable;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import java.util.Locale;//add by lipeng
import android.view.Gravity;//add by lipeng
import android.text.util.Linkify;//add by lipeng
import android.os.SystemProperties;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
/// @}
/**
 * This class provides view of a message in the messages list.
 */
public class MessageListItem extends LinearLayout implements
        SlideViewInterface, OnClickListener, INotificationsListener {
    public static final String EXTRA_URLS = "com.android.mms.ExtraUrls";

    private static final String TAG = "MessageListItem";
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_DONT_LOAD_IMAGES = false;

    private static final String M_TAG = "Mms/MessageListItem";
    /// M: add for ip message
    private static final String IPMSG_TAG = "Mms/ipmsg/MessageListItem";
    public static final String TAG_DIVIDER = "Mms/divider";

    private static final StyleSpan STYLE_BOLD = new StyleSpan(Typeface.BOLD);
    private static HashMap<Integer, String> mHashSub = new HashMap<Integer, String>();
    private String mDefualtSubName = "SUB01";

    static final int MSG_LIST_EDIT    = 1;
    static final int MSG_LIST_PLAY    = 2;
    static final int MSG_LIST_DETAILS = 3;

    static final int MSG_LIST_EDIT_MMS   = 1;
    static final int MSG_LIST_EDIT_SMS   = 2;
    private static final int PADDING_LEFT_THR = 3;
    private static final int PADDING_LEFT_TWE = 13;

    private View mMmsView;
    /// M: add for vcard
    private View mFileAttachmentView;
    private ImageView mImageView;
    private ImageView mLockedIndicator;
    private ImageView mDeliveredIndicator;
    private ImageView mDetailsIndicator;
    private ImageButton mSlideShowButton;
    private TextView mBodyTextView;
    private Button mDownloadButton;
    private TextView mDownloadingLabel;
    private Handler mHandler;
    private MessageItem mMessageItem;
    private String mDefaultCountryIso;
    private TextView mDateView;
	private TextView mSendDeliveryStatus;/*HQ_zhangjing 2015-10-26 modified for CQ  HQ01456345 begin*/
//    public View mMessageBlock;
    private Path mPathRight;
    private Path mPathLeft;
    private Paint mPaint;
    private QuickContactDivot mAvatar;
    private boolean mIsLastItemInList;
    static private Drawable sDefaultContactImage;
    private Presenter mPresenter;
    private int mPosition;      // for debugging
    private ImageLoadedCallback mImageLoadedCallback;

    /// M: google JB.MR1 patch, group mms
    private boolean mIsGroupMms;
    /// M: fix bug ALPS00439894, MTK MR1 new feature: Group Mms
    private QuickContactBadge mSenderPhoto;
    private TextView mSenderName;
    private View mSendNameSeparator;

    private Toast mInvalidContactToast;
    private LeadingMarginSpan mLeadingMarginSpan;
    private long mHandlerTime = 0;
    /// M:
    private static  boolean sImageButtonCanClick = true; // this is a hack for quick click.

    /// M: add for ip message
    /// M: add for image and video
    private View mIpImageView; // ip_image
    private ImageView mImageContent; // image_content
    private View mIpImageSizeBg; // image_size_bg
    private ImageView mActionButton; // action_btn
    private TextView mContentSize; // content_size
    private ProgressBar mImageDownloadProgressBar; // image_downLoad_progress"
    //private View mCaptionSeparator; // caption_separator
    private TextView mCaption; // text_caption
    private ImageView mMediaPlayView;
//    private View mVideoCaptionSeparator; // caption_separator
//    private TextView mVideoCaption; // text_caption
    /// M: add for audio
    private View mIpAudioView; // ip_audio
    private ImageView mAudioIcon; // ip_audio_icon
    private TextView mAudioInfo; // audio_info
    private ProgressBar mAudioDownloadProgressBar; // audio_downLoad_progress
    /// M: add for vcard
    private View mIpVCardView;
    private TextView mVCardInfo;
    /// M: add for vcalendar
    private View mIpVCalendarView;
    private TextView mVCalendarInfo;
    /// M: add for time divider
    private View mTimeDivider; // time_divider
    private TextView mTimeDividerStr; // time_divider_str
    /// M: add for unread divider
    private View mUnreadDivider; // unread_divider
    private TextView mUnreadDividerStr; // unread_divider_str
    /// M: add for online divider
    private View mOnLineDivider; // on_line_divider
    private TextView mOnLineDividertextView; // on_line_divider_str
    /// M: add for sub message divider
    private View mSubDivider;
    private TextView mExpireText;
    /// M: add for ip message, download file, accept or reject
    private View mIpmsgFileDownloadContrller; // ipmsg_file_downloading_controller_view
    private TextView mIpmsgResendButton; // ipmsg_resend
    private Button mIpmsgAcceptButton; // ipmsg_accept
    private Button mIpmsgRejectButton; // ipmsg_reject
    private View mIpmsgFileDownloadView; // ipmsg_file_download
    private TextView mIpmsgFileSize; // ipmsg_download_file_size
    private ImageView mIpmsgCancelDownloadButton; // ipmsg_download_file_cancel
    private ImageView mIpmsgPauseResumeButton; // ipmsg_download_file_resume
    private ProgressBar mIpmsgDownloadFileProgress; // ipmsg_download_file_progress

    private boolean isRegistNotificationListener = false;

    private MessageListAdapter mMessageListAdapter;

    public static final int MSG_LIST_RESEND_IPMSG = 20;

    private final static float MAX_SCALE = 0.4f;
    private final static float MIN_SCALE = 0.3f;
    private final static float COMP_NUMBER = 0.5f;
    private final static int GIF_VIEW_SIZE_SMALL = 128;
    private final static int GIF_VIEW_SIZE_BIG = 160;
    
	private static AlertDialog da;

    /// M: add for OP09 Plug-in.
    private IMmsMessageListItemExt mMmsMessageListItemPlugin = null;
    /// @}
    public static boolean sClickCanResponse = true;

    public MessageListItem(Context context) {
        super(context);
        mDefaultCountryIso = MmsApp.getApplication().getCurrentCountryIso();

        if (sDefaultContactImage == null) {
            sDefaultContactImage = context.getResources().getDrawable(R.drawable.ic_contact_picture);
        }
        initPlugin(this.getContext());
    }

    public MessageListItem(Context context, AttributeSet attrs) {
        super(context, attrs);

        int color = mContext.getResources().getColor(R.color.timestamp_color);
        mColorSpan = new ForegroundColorSpan(color);
        mDefaultCountryIso = MmsApp.getApplication().getCurrentCountryIso();
        if (sDefaultContactImage == null) {
            sDefaultContactImage = context.getResources().getDrawable(R.drawable.ic_contact_picture);
        }
        initPlugin(this.getContext());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mBodyTextView = (TextView) findViewById(R.id.text_view);
        
        if(SystemProperties.get("ro.hq.mms.number.link").equals("1")){ // lipeng to do LIPENG || MmsConfig.getEnableMsk()
        	mBodyTextView.setAutoLinkMask(Linkify.ALL_MMS);
        }
        
        mDateView = (TextView) findViewById(R.id.date_view);
        /// M: @{
        mSubStatus = (TextView) findViewById(R.id.sim_status);
        /// @}
        mSimIndicator = ( ImageView )findViewById( R.id.sim_indicator );
        mLockedIndicator = (ImageView) findViewById(R.id.locked_indicator);
        mDeliveredIndicator = (ImageView) findViewById(R.id.delivered_indicator);
		/*HQ_zhangjing 2015-10-26 modified for CQ  HQ01456345 */
		mSendDeliveryStatus = (TextView) findViewById(R.id.delivered_status_txt);
        mDetailsIndicator = (ImageView) findViewById(R.id.details_indicator);
        mAvatar = (QuickContactDivot) findViewById(R.id.avatar);
        /// M: Remove Google default code
//        mMessageBlock = findViewById(R.id.message_block);
        /// M: @{
        //add for multi-delete
        mSelectedBox = (CheckBox) findViewById(R.id.select_check_box);
        /// @}

        /// M: add for ip message
        /// M: add for image and video
        mIpImageView = (View) findViewById(R.id.ip_image);
        mImageContent = (ImageView) findViewById(R.id.image_content);
        mIpImageSizeBg = (View) findViewById(R.id.image_size_bg);
        mActionButton = (ImageView) findViewById(R.id.action_btn);
        mContentSize = (TextView) findViewById(R.id.content_size);
        mImageDownloadProgressBar = (ProgressBar) findViewById(R.id.image_downLoad_progress);
        //mCaptionSeparator = (View) findViewById(R.id.caption_separator);
        mCaption = (TextView) findViewById(R.id.text_caption);
        mMediaPlayView = (ImageView) findViewById(R.id.video_media_paly);
        /// M: add for audio
        mIpAudioView = (View) findViewById(R.id.ip_audio);
        mAudioIcon = (ImageView) findViewById(R.id.ip_audio_icon);
        mAudioInfo = (TextView) findViewById(R.id.audio_info);
        mAudioDownloadProgressBar = (ProgressBar) findViewById(R.id.audio_downLoad_progress);
        /// M: add for vCard
        mIpVCardView = (View) findViewById(R.id.ip_vcard);
        mVCardInfo = (TextView) findViewById(R.id.vcard_info);
        /// M: add for vCalendar
        mIpVCalendarView = (View) findViewById(R.id.ip_vcalendar);
        mVCalendarInfo = (TextView) findViewById(R.id.vcalendar_info);
        /// M: add for time divider
        mTimeDivider = (View) findViewById(R.id.time_divider);
        if (null != mTimeDivider) {
            mTimeDividerStr = (TextView) mTimeDivider.findViewById(R.id.time_divider_str);
        }
        mUnreadDivider = (View) findViewById(R.id.unread_divider);
        if (null != mUnreadDivider) {
            mUnreadDividerStr = (TextView) mUnreadDivider.findViewById(R.id.unread_divider_str);
        }
        mOnLineDivider = (View) findViewById(R.id.on_line_divider);
        if (null != mOnLineDivider) {
            mOnLineDividertextView = (TextView) mOnLineDivider.findViewById(R.id.on_line_divider_str);
        }
        mSubDivider = (View) findViewById(R.id.sim_divider);
        mExpireText = (TextView) findViewById(R.id.text_expire);

        /// M: add for ip message, file download
        if (IpMessageUtils.getServiceManager(mContext).isFeatureSupported(FeatureId.FILE_TRANSACTION)) {
            mIpmsgFileDownloadContrller = (View) findViewById(R.id.ipmsg_file_downloading_controller_view);
            mIpmsgResendButton = (TextView) findViewById(R.id.ipmsg_resend);
            mIpmsgAcceptButton = (Button) findViewById(R.id.ipmsg_accept);
            mIpmsgRejectButton = (Button) findViewById(R.id.ipmsg_reject);
            mIpmsgFileDownloadView = (View) findViewById(R.id.ipmsg_file_download);
            mIpmsgFileSize = (TextView) findViewById(R.id.ipmsg_download_file_size);
            mIpmsgCancelDownloadButton = (ImageView) findViewById(R.id.ipmsg_download_file_cancel);
            mIpmsgPauseResumeButton = (ImageView) findViewById(R.id.ipmsg_download_file_resume);
            mIpmsgDownloadFileProgress = (ProgressBar) findViewById(R.id.ipmsg_download_file_progress);
        }
    }

    public void bind(MessageItem msgItem, boolean convGroupMms, int position, boolean isDeleteMode) {
        if (msgItem == null) {
            /// M: google jb.mr1 patch, group mms. isLastItem (useless) ? convHasMultiRecipients
            boolean isLastItem = convGroupMms;
            bindDefault(null, isLastItem);
            MmsLog.i(TAG, "bind: msgItem is null, position = " + position);
            return;
        }
        /// M: fix bug ALPS00383381 @{
        MmsLog.i(TAG, "MessageListItem.bind() : msgItem.mSubId = " + msgItem.mSubId
                + ", position = " + position +
                "uri = " + msgItem.mMessageUri);
        /// @}
        mMessageItem = msgItem;
        mIsGroupMms = convGroupMms;
        mPosition = position;
        /// fix bug ALPS00400536, set null text to avoiding reuse view @{
        mBodyTextView.setText("");
        /// @}

        /*HQ_zhangjing add for al812 mms UI begin*/
		if (TelephonyManager.getDefault().isMultiSimEnabled()) {
			Log.d(TAG,"zhangjing : sim indicator");
			if( mSimIndicator != null ){
				Log.d(TAG,"zhangjing : mSimIndicator != nullr");
					mSimIndicator.setVisibility(View.VISIBLE);
					 SubscriptionInfo subInfo = SubscriptionManager.from(MmsApp.getApplication())
							 .getActiveSubscriptionInfo(mMessageItem.mSubId);
					 int slotId = -1;
					 if( subInfo != null ){
						Log.i(TAG, "subInfo=" + subInfo);
					 	slotId = subInfo.getSimSlotIndex();
					 }
					 int simResouseId = R.drawable.icon_card_sim_none;
					 if( slotId == SubscriptionManager.SIM_NOT_INSERTED ){
						 simResouseId = R.drawable.icon_card_sim_none;
					 }else{
						 simResouseId = (slotId == 0?R.drawable.icon_card_sim1 : R.drawable.icon_card_sim2);
					 }
					 mSimIndicator.setImageDrawable(mContext.getResources().getDrawable( simResouseId ));
				}
		}
        /*HQ_zhangjing add nd*/


        /// M: fix bug ALPS00439894, MTK MR1 new feature: Group Mms
        /// set Gone to avoiding reuse view (Visible)
        if (!mMessageItem.isMe()) {
            mSenderName = (TextView) findViewById(R.id.sender_name);
            mSendNameSeparator = findViewById(R.id.sender_name_separator);
            mSenderPhoto = (QuickContactBadge) findViewById(R.id.sender_photo);
            if (mSenderName != null && mSenderPhoto != null && mSendNameSeparator != null) {
                mSenderName.setVisibility(View.GONE);
                mSendNameSeparator.setVisibility(View.GONE);
                mSenderPhoto.setVisibility(View.GONE);
            }
        }

        /// M: @{
        if (isDeleteMode) {
            mSelectedBox.setVisibility(View.VISIBLE);
            if (msgItem.isSelected()) {
                setSelectedBackGroud(true);
            }else {
                setSelectedBackGroud(false);
            }
        } else {
            /// M: change for ALPS01899925, set the background as null if not in delete mode. @{
            if (msgItem.isSubMsg()) {
                setSelectedBackGroud(false);
            }
            /// @}
            mSelectedBox.setVisibility(View.GONE);
        }
        /// M: @{

        setLongClickable(false);
        //set item these two false can make listview always get click event.
        setFocusable(false);
        setClickable(false);

        /// M: add for ip message
        mContext = msgItem.mContext;
        MmsLog.d(IPMSG_TAG, "bindView(): IpMessageId = " + msgItem.mIpMessageId);
        if (!msgItem.isSubMsg()) {
            bindDividers(msgItem, isDeleteMode);
            if (msgItem.mIpMessageId > 0
                    && IpMessageUtils.getIpMessagePlugin(mContext).isActualPlugin()
                    && IpMessageUtils.getServiceManager(mContext).isFeatureSupported(
                            IpMessageConsts.FeatureId.PARSE_EMO_WITHOUT_ACTIVATE)) {
                bindIpmsg(msgItem, isDeleteMode);
                return;
            }
        }

        if (mSubDivider != null) {
            if (msgItem.isSubMsg() && (position > 0)) {
			/*HQ_zhangjing add for al812 mms ui */
                //mSubDivider.setVisibility(View.VISIBLE);
            } else {
                mSubDivider.setVisibility(View.GONE);
            }
        }

        switch (msgItem.mMessageType) {
            case PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND:
                bindNotifInd();
                break;
            default:
                bindCommonMessage();
                break;
        }
       // setSubIconAndLabel();
    }

    public void unbind() {
        ///M: fix bug ALPS00383381 @{
        MmsLog.i(TAG, "unbind() :  " + " position = " + mPosition + "uri = " +
                (mMessageItem == null ? "" : mMessageItem.mMessageUri));
        /// @}
        // Clear all references to the message item, which can contain attachments and other
        // memory-intensive objects
        mMessageItem = null;
        if (mImageView != null) {
            // Because #setOnClickListener may have set the listener to an object that has the
            // message item in its closure.
            mImageView.setOnClickListener(null);
        }
        if (mSlideShowButton != null) {
            // Because #drawPlaybackButton sets the tag to mMessageItem
            mSlideShowButton.setTag(null);
        }
        // leave the presenter in case it's needed when rebound to a different MessageItem.
        if (mPresenter != null) {
            mPresenter.cancelBackgroundLoading();
        }
    }

    public MessageItem getMessageItem() {
        return mMessageItem;
    }

    public void setMsgListItemHandler(Handler handler) {
        mHandler = handler;
    }

    private void bindNotifInd() {
        showMmsView(false);
        // add for vcard
        hideFileAttachmentViewIfNeeded();

        /// M: fix bug ALPS00423228, reuse last view when refresh ListView @{
        mDateView.setText("");
        /// @}

        String msgSizeText = mContext.getString(R.string.message_size_label)
                                + String.valueOf((mMessageItem.mMessageSize + 1023) / 1024)
                                + mContext.getString(R.string.kilobyte);

        mBodyTextView.setVisibility(View.VISIBLE);
        mBodyTextView.setText(formatMessage(mMessageItem, null,
                              mMessageItem.mSubject,
                              mMessageItem.mHighlight,
                              mMessageItem.mTextContentType));
        /// M:
        mExpireText.setText(msgSizeText + "\t\n" + mMessageItem.mTimestamp);
        mExpireText.setVisibility(View.VISIBLE);
		//modify by lipeng for number display
        String infos = mMessageItem.mAddress;
        String langage = Locale.getDefault().getLanguage();//add by lipeng for number display
        if(infos!=null && MessageUtils.isInteger(infos)&&(langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw"))){
               infos = "\u202D"+infos+"\u202C";
        }
        /// M: add for OP09 feature. @{
        mMmsMessageListItemPlugin.setNotifyContent(mContext.getString(R.string.from_label)
                + infos, mContext.getString(R.string.subject_label)
                + (mMessageItem.mSubject != null ? mMessageItem.mSubject : ""), msgSizeText,
                mMessageItem.mTimestamp, mExpireText);
        /// @}
        /// M: Google jb.mr1 patch
        // mDateView.setText(buildTimestampLine(msgSizeText + " " + mMessageItem.mTimestamp));

        /// M: @{
        mSubStatus.setVisibility(View.VISIBLE);
        //mSubStatus.setText(formatSubStatus(mMessageItem));
        setSubIconAndLabel(null);
        /// M: Add for OP09 {
        MmsLog.d(TAG, "MessageListItem SubId:" + mMessageItem.mSubId);
		//HQ_zhangjing 2015-10-24 modified for CQ  HQ01459241
        //mMmsMessageListItemPlugin.showSimType(this.getContext(), mMessageItem.mSubId, mSubStatus);
        /// @}
        MmsLog.i(TAG, "bindNotifInd: uri = " + mMessageItem.mMessageUri +
                    ", position = " + mPosition + ", downloading Status ="
                    + mMessageItem.getMmsDownloadStatus());
        switch (mMessageItem.getMmsDownloadStatus()) {
            case DownloadManager.STATE_DOWNLOADING:
                showDownloadingAttachment();
                /// M: @{
                findViewById(R.id.text_view).setVisibility(GONE);
                /// @}
                break;
            case DownloadManager.STATE_UNKNOWN:
            case DownloadManager.STATE_UNSTARTED:
                /** M: comment this code, this code bug fix is not perfect. there is other bigger bugs
                DownloadManager downloadManager = DownloadManager.getInstance();
                boolean autoDownload = downloadManager.isAuto();
                boolean dataSuspended = (MmsApp.getApplication().getTelephonyManager()
                        .getDataState() == TelephonyManager.DATA_SUSPENDED);

                // If we're going to automatically start downloading the mms attachment, then
                // don't bother showing the download button for an instant before the actual
                // download begins. Instead, show downloading as taking place.
                if (autoDownload && !dataSuspended) {
                    showDownloadingAttachment();
                    break;
                }
                */
            case DownloadManager.STATE_TRANSIENT_FAILURE:
            case DownloadManager.STATE_PERMANENT_FAILURE:
            default:
                setLongClickable(true);
                inflateDownloadControls();
                mDownloadingLabel.setVisibility(View.GONE);
                mDownloadButton.setVisibility(View.VISIBLE);
                /// M: @{
                findViewById(R.id.text_view).setVisibility(GONE);
                /// @}
                mDownloadButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ///M: When it's not default SMS, do not download
                        if (!MmsConfig.isSmsEnabled(mContext)) {
                            Toast.makeText(mContext, R.string.download_disabled_toast, Toast.LENGTH_LONG).show();
                            return;
                        }

                        ///M: fix bug ALPS00383381 @{
                        // avoid mMessageItem is already setted null
                        if (mMessageItem == null || mMessageItem.mMessageUri == null) {
                            MmsLog.v(TAG, "downloadButton onClick, mMessageItem or mMessageUri is null");
                            return;
                        }
                        //@}

                        /// M: @{
                        //add for multi-delete
                        if (mSelectedBox != null && mSelectedBox.getVisibility() == View.VISIBLE) {
                            return;
                        }
                        MmsLog.i(TAG, "bindNotifInd: download button onClick: uri = " + mMessageItem.mMessageUri +
                                ", position = " + mPosition);
                        /// M: add for OP09 feature. @{
                        IMmsUtilsExt mMmsUtils = (IMmsUtilsExt) MmsPluginManager
                                .getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_MESSAGE_UTILS);
                        if (mMmsUtils != null
                            && !mMmsUtils.allowSafeDraft(ComposeMessageActivity.getComposeContext(), MmsConfig
                                    .getDeviceStorageFullStatus(), true, IMmsUtilsExt.TOAST_TYPE_FOR_DOWNLOAD_MMS)) {
                            return;
                        }
                        //@}
                        // add for gemini
                        int subId = 0;
                        // get sub id by uri
                        Cursor cursor = SqliteWrapper.query(mMessageItem.mContext,
                                mMessageItem.mContext.getContentResolver(),
                                mMessageItem.mMessageUri, new String[] {
                                    Telephony.Mms.SUBSCRIPTION_ID
                                }, null, null, null);
                        if (cursor != null) {
                            try {
                                if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                                    subId = cursor.getInt(0);
                                }
                            } finally {
                                cursor.close();
                            }
                        }

                        /// M: New plugin API @{
                        if (MmsConfig.getDeviceStorageFullStatus()) {
                            if (mMmsMessageListItemPlugin.showStorageFullToast(MmsApp.getApplication())) {
                                return;
                            }
                        }
                        /// @}
                        /// M: fix bug ALPS00406912
                        mMessageItem.mMmsStatus = DownloadManager.STATE_DOWNLOADING;
                        DownloadManager.getInstance().markState(mMessageItem.mMessageUri, DownloadManager.STATE_DOWNLOADING);
                        if (mMessageListAdapter != null) {
                            mMessageListAdapter.saveNotifIndStatus(mMessageItem.getMessageId(), DownloadManager.STATE_DOWNLOADING);
                        }

                        mDownloadingLabel.setVisibility(View.VISIBLE);
                        mDownloadButton.setVisibility(View.GONE);
                        Intent intent = new Intent(mContext, TransactionService.class);
                        intent.putExtra(TransactionBundle.URI, mMessageItem.mMessageUri.toString());
                        intent.putExtra(TransactionBundle.TRANSACTION_TYPE,
                                Transaction.RETRIEVE_TRANSACTION);
                        // add for gemini
                        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);
                        mContext.startService(intent);
                    }
                });
                //mtk81083 this is a google default bug. it has no this code!
                // When we show the mDownloadButton, this list item's onItemClickListener doesn't
                // get called. (It gets set in ComposeMessageActivity:
                // mMsgListView.setOnItemClickListener) Here we explicitly set the item's
                // onClickListener. It allows the item to respond to embedded html links and at the
                // same time, allows the button to work.
                setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onMessageListItemClick();
                    }
                });
                /// M: add for OP09. @{
                if (MmsConfig.isSmsEnabled(mContext)) {
                    mMmsMessageListItemPlugin.showDownloadButton(mMessageItem.mMessageUri,
                            mSelectedBox, mMessageItem.getMessageId(),
                            MmsConfig.getDeviceStorageFullStatus(), mDownloadBtnListener,
                            mCancelDownloadListener);
                }
                //@}
                break;
        }

        // Hide the indicators.
        /// M: @{
        //mLockedIndicator.setVisibility(View.GONE);
        if (mMessageItem.mLocked) {
            mLockedIndicator.setImageResource(R.drawable.ic_lock_message_sms);
            mLockedIndicator.setVisibility(View.VISIBLE);
            mSubStatus.setPadding(PADDING_LEFT_THR, 0, 0, 0);
        } else {
            mLockedIndicator.setVisibility(View.GONE);
            mSubStatus.setPadding(PADDING_LEFT_TWE, 0, 0, 0);
        }
        /// @}
        mDeliveredIndicator.setVisibility(View.GONE);
        mDetailsIndicator.setVisibility(View.GONE);
        /// M: Remove Google default code
//        updateAvatarView(msgItem.mAddress, false);
        if (MmsConfig.isShowDualTimeForMsgItemEnable()) {
            mMmsMessageListItemPlugin.hideDualTimePanel((TextView) findViewById(R.id.send_time_txt),
                (LinearLayout) findViewById(R.id.double_time_layout));
        }
    }

    /// M: google JB.MR1 patch, group mms
    private String buildTimestampLine(String timestamp) {
        if (!mIsGroupMms || mMessageItem.isMe() || TextUtils.isEmpty(mMessageItem.mContact)) {
            // Never show "Me" for messages I sent.
            return timestamp;
        }

        /// M: fix bug ALPS00439894, MTK MR1 new feature: Group Mms
        if (mSenderName != null && mSenderPhoto != null && mSendNameSeparator != null) {
            mSendNameSeparator.setVisibility(View.VISIBLE);
            mSenderName.setText(mMessageItem.mContact);
            mSenderName.setVisibility(View.VISIBLE);
            Drawable avatarDrawable;
            if (mMessageItem.mGroupContact != null) {
                avatarDrawable = mMessageItem.mGroupContact.getAvatar(mContext, sDefaultContactImage, -1);
            } else {
                avatarDrawable = sDefaultContactImage;
            }
            mSenderPhoto.setImageDrawable(avatarDrawable);
            mSenderPhoto.setVisibility(View.VISIBLE);

            // mSenderPhoto.setClickable(false);
            String number = mMessageItem.mGroupContact.getNumber();
            if (Mms.isEmailAddress(number)) {
                mSenderPhoto.assignContactFromEmail(number, true);
            } else {
                if (mMessageItem.mGroupContact.existsInDatabase()) {
                    mSenderPhoto.assignContactUri(mMessageItem.mGroupContact.getUri());
                } else {
                    mSenderPhoto.assignContactFromPhone(number, true);
                }
            }
        }

        // This is a group conversation, show the sender's name on the same line as the timestamp.
        return timestamp;
    }

    private void showDownloadingAttachment() {
        inflateDownloadControls();
        mDownloadingLabel.setVisibility(View.VISIBLE);
        mDownloadButton.setVisibility(View.GONE);
        /// M: add for OP09. @{
        mMmsMessageListItemPlugin.hideDownloadButton(mMessageItem.mMessageUri, mCancelDownloadListener, mSelectedBox, mMessageItem
                .getMessageId());
        /// @}
    }

    private void updateAvatarView(String addr, boolean isSelf) {
        Drawable avatarDrawable;
        if (isSelf || !TextUtils.isEmpty(addr)) {
            Contact contact = isSelf ? Contact.getMe(false) : Contact.get(addr, false);
            avatarDrawable = contact.getAvatar(mContext, sDefaultContactImage, -1);

            if (isSelf) {
                mAvatar.assignContactUri(Profile.CONTENT_URI);
            } else {
                String number = contact.getNumber();
                if (Mms.isEmailAddress(number)) {
                    mAvatar.assignContactFromEmail(number, true);
                } else {
                    mAvatar.assignContactFromPhone(number, true);
                }
            }
        } else {
            avatarDrawable = sDefaultContactImage;
        }
        mAvatar.setImageDrawable(avatarDrawable);
    }

    private void bindCommonMessage() {
        if (mDownloadButton != null) {
            mDownloadButton.setVisibility(View.GONE);
            mDownloadingLabel.setVisibility(View.GONE);
            /// M: @{
            mBodyTextView.setVisibility(View.VISIBLE);
            /// @}
            /// M: add for OP09. @{
            mMmsMessageListItemPlugin.hideAllButton();
            /// @}
        }

        /// M: only mms notifInd view will use and show this.
        if (mExpireText != null) {
            mExpireText.setVisibility(View.GONE);
        }

        // Since the message text should be concatenated with the sender's
        // address(or name), I have to display it here instead of
        // displaying it by the Presenter.
        mBodyTextView.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
        if (mAvatar != null) {
            if (mMessageItem.isSubMsg()) {
                mAvatar.setVisibility(View.VISIBLE);
                boolean isSelf = Sms.isOutgoingFolder(mMessageItem.mBoxId);
                String addr = isSelf ? null : mMessageItem.mAddress;
                updateAvatarView(addr, isSelf);
            } else {
                mAvatar.setVisibility(View.GONE);
            }
        }

        // Get and/or lazily set the formatted message from/on the
        // MessageItem.  Because the MessageItem instances come from a
        // cache (currently of size ~50), the hit rate on avoiding the
        // expensive formatMessage() call is very high.
        CharSequence formattedMessage = mMessageItem.getCachedFormattedMessage();
        /// M: @{
        // CharSequence formattedTimestamp = mMessageItem.getCachedFormattedTimestamp();
//        CharSequence formattedSubStatus = mMessageItem.getCachedFormattedSubStatus();
        /// @}

        if (formattedMessage == null) {
            formattedMessage = formatMessage(mMessageItem,
                                             mMessageItem.mBody,
                                             mMessageItem.mSubject,
                                             mMessageItem.mHighlight,
                                             mMessageItem.mTextContentType);
            /// M: @{
            // formattedTimestamp = formatTimestamp(mMessageItem, mMessageItem.mTimestamp);
//            formattedSubStatus = formatSubStatus(mMessageItem);
//            mMessageItem.setCachedFormattedSubStatus(formattedSubStatus);
            /// @}
            mMessageItem.setCachedFormattedMessage(formattedMessage);
        }
        /// M:
        if (TextUtils.isEmpty(mMessageItem.mBody) && TextUtils.isEmpty(mMessageItem.mSubject)) {
            mBodyTextView.setVisibility(View.GONE);
        } else {
            mBodyTextView.setVisibility(View.VISIBLE);
            //add by wanghui for msg
            Pattern patmodel = Pattern.compile("(,|\\.|!|\\?|\\s|[a-zA-Z])+");
            String langage = Locale.getDefault().getLanguage();
            if(langage.startsWith("ar")){
                if(patmodel.matcher(formattedMessage).matches()){
            	    mBodyTextView.setGravity(Gravity.LEFT);
            	    Log.d("HQWH","match");
                }else{
            	    mBodyTextView.setGravity(Gravity.RIGHT);
            	    Log.d("HQWH","no match");
                 }
            }
            mBodyTextView.setText(formattedMessage);
        }

        // Debugging code to put the URI of the image attachment in the body of the list item.
        if (DEBUG) {
            String debugText = null;
            if (mMessageItem.mSlideshow == null) {
                debugText = "NULL slideshow";
            } else {
                SlideModel slide = ((SlideshowModel) mMessageItem.mSlideshow).get(0);
                if (slide == null) {
                    debugText = "NULL first slide";
                } else if (!slide.hasImage()) {
                    debugText = "Not an image";
                } else {
                    debugText = slide.getImage().getUri().toString();
                }
            }
            mBodyTextView.setText(mPosition + ": " + debugText);
        }

        // If we're in the process of sending a message (i.e. pending), then we show a "SENDING..."
        // string in place of the timestamp.
        /// M: @{
        /*mDateView.setText(msgItem.isSending() ?
                mContext.getResources().getString(R.string.sending_message) :
                    buildTimestampLine(msgItem.mTimestamp));
        */
		/*HQ_zhangjing 2015-10-08 modified for CQ HQ01432443 */
        if ((!mMessageItem.isSending() && TextUtils.isEmpty(mMessageItem.mTimestamp))) {
            mDateView.setVisibility(View.GONE);
        } else {
            mDateView.setVisibility(View.VISIBLE);
            /// M: google jb.mr1 patch, group mms
            /// M: Modify for OP09. @{
            String dateStr = mMessageItem.isSending() ?
                    mContext.getResources().getString(R.string.sending_message) : buildTimestampLine(mMessageItem.mTimestamp);
            if (MmsConfig.isShowDateManagementEnable()) {
                dateStr = mMmsMessageListItemPlugin.getSentDateStr(this.getContext(), dateStr,
                        mMessageItem.getMessageId(), mMessageItem.mMessageType, mMessageItem.mSmsSentDate, mMessageItem.mBoxId);
            }
            mDateView.setText(dateStr);
            /// @}
        }
        /// @}

        /// M: @{
        //if (!mMessageItem.isSubMsg() && !TextUtils.isEmpty(formattedSubStatus)) {
        if (!mMessageItem.isSubMsg()) {
            mSubStatus.setVisibility(View.VISIBLE);
           // mSubStatus.setText(formattedSubStatus);
            setSubIconAndLabel(null);
        } else {
            mSubStatus.setVisibility(View.GONE);
        }
        /// @}

        if (mMessageItem.isSms()) {
            showMmsView(false);
            mMessageItem.setOnPduLoaded(null);
            // add for vcard
            hideFileAttachmentViewIfNeeded();
        } else {
            if (DEBUG) {
                Log.v(TAG, "bindCommonMessage for item: " + mPosition + " " +
                        mMessageItem.toString() +
                        " mMessageItem.mAttachmentType: " + mMessageItem.mAttachmentType);
            }
            boolean isShowFileAttachmentView = false;
            if (mMessageItem.mAttachmentType != WorkingMessage.TEXT &&
                    mMessageItem.mAttachmentType != MessageItem.ATTACHMENT_TYPE_NOT_LOADED) {
                if (mMessageItem.mAttachmentType == WorkingMessage.ATTACHMENT) {
                    isShowFileAttachmentView = true;
                    showMmsView(false);
                    // show file attachment view
                    showFileAttachmentView(mMessageItem.mSlideshow.getAttachFiles());
                } else {
                    /// M: add for VCard and VCanlendar
                    if (MmsConfig.isSupportAttachEnhance()) {
                        MmsLog.d(TAG, "attachEnhancePlugin.isSupportAttachmentEnhance() == true");
                        showFileAttachmentView(mMessageItem.mSlideshow.getAttachFiles());
                        if (mMessageItem.mSlideshow.getAttachFiles() == null ||
                            mMessageItem.mSlideshow.getAttachFiles().size() < 1) {
                            MmsLog.e(TAG, "hideFileAttachmentViewIfNeeded ");
                            hideFileAttachmentViewIfNeeded();
                        }
                    } else {
                        hideFileAttachmentViewIfNeeded();
                        MmsLog.i(TAG, "mMmsAttachmentEnhancePlugin= null");
                    }
                    setImage(null, null);

                    drawPlaybackButton(mMessageItem);
			//modify by liruihong for HQ01432523
                   // if (mSlideShowButton.getVisibility() == View.GONE) {
                        setMediaOnClickListener(mMessageItem);
                   // }
                }
            } else {
                showMmsView(false);
                /// M:  add for VCard and VCanlendar
                hideFileAttachmentViewIfNeeded();
            }
            if (mMessageItem.mSlideshow == null && !isShowFileAttachmentView) {
                mMessageItem.setOnPduLoaded(new MessageItem.PduLoadedCallback() {
                    public void onPduLoaded(MessageItem messageItem) {
                        if (DEBUG) {
                            Log.v(TAG, "PduLoadedCallback in MessageListItem for item: " + mPosition +
                                    " " + (mMessageItem == null ? "NULL" : mMessageItem.toString()) +
                                    " passed in item: " +
                                    (messageItem == null ? "NULL" : messageItem.toString()));
                        }
                        if (messageItem != null && mMessageItem != null &&
                                messageItem.getMessageId() == mMessageItem.getMessageId()) {
                            mMessageItem.setCachedFormattedMessage(null);
                            bindCommonMessage();
                        }
                    }
                });
            } else {
                if (mPresenter == null) {
                    mPresenter = PresenterFactory.getPresenter(
                    "MmsThumbnailPresenter", mContext,
                    this, mMessageItem.mSlideshow);
                } else {
                    mPresenter.setModel(mMessageItem.mSlideshow);
                    mPresenter.setView(this);
                }
                if (mImageLoadedCallback == null) {
                    mImageLoadedCallback = new ImageLoadedCallback(this);
                } else {
                    mImageLoadedCallback.reset(this);
                }
                mPresenter.present(mImageLoadedCallback);
            }
        }
        drawRightStatusIndicator(mMessageItem);

        requestLayout();
    }

    static private class ImageLoadedCallback implements ItemLoadedCallback<ImageLoaded> {
        private long mMessageId;
        private final MessageListItem mListItem;

        public ImageLoadedCallback(MessageListItem listItem) {
            mListItem = listItem;
            mMessageId = listItem.getMessageItem().getMessageId();
        }

        public void reset(MessageListItem listItem) {
            mMessageId = listItem.getMessageItem().getMessageId();
        }

        public void onItemLoaded(ImageLoaded imageLoaded, Throwable exception) {
            if (DEBUG_DONT_LOAD_IMAGES) {
                return;
            }
            // Make sure we're still pointing to the same message. The list item could have
            // been recycled.
            MessageItem msgItem = mListItem.mMessageItem;
            if (msgItem != null && msgItem.getMessageId() == mMessageId) {
                if (imageLoaded.mIsVideo) {
                    mListItem.setVideoThumbnail(null, imageLoaded.mBitmap);
                } else {
                    mListItem.setImage(null, imageLoaded.mBitmap);
                }
            }
        }
    }

    @Override
    public void startAudio() {
        // TODO Auto-generated method stub
    }

    @Override
    public void startVideo() {
        // TODO Auto-generated method stub
    }

    @Override
    public void setAudio(Uri audio, String name, Map<String, ?> extras) {
        // TODO Auto-generated method stub
    }

    @Override
    public void setImage(String name, Bitmap bitmap) {
        showMmsView(true);

        try {
            mImageView.setImageBitmap(bitmap);
            mImageView.setVisibility(VISIBLE);
            /// M:
            if (null != mMediaPlayView && mMediaPlayView.getVisibility() == View.VISIBLE) {
                mMediaPlayView.setVisibility(View.GONE);
            }
        } catch (java.lang.OutOfMemoryError e) {
            Log.e(TAG, "setImage: out of memory: ", e);
            MessageUtils.writeHprofDataToFile();
        }
    }

    private void showMmsView(boolean visible) {
        if (mMmsView == null) {
            mMmsView = findViewById(R.id.mms_view);
            // if mMmsView is still null here, that mean the mms section hasn't been inflated

            if (visible && mMmsView == null) {
                //inflate the mms view_stub
                View mmsStub = findViewById(R.id.mms_layout_view_stub);
                mmsStub.setVisibility(View.VISIBLE);
                mMmsView = findViewById(R.id.mms_view);
            }
        }
        if (mMmsView != null) {
            if (mImageView == null) {
                mImageView = (ImageView) findViewById(R.id.image_view);
            }
            if (mSlideShowButton == null) {
                mSlideShowButton = (ImageButton) findViewById(R.id.play_slideshow_button);
            }
            mMmsView.setVisibility(visible ? View.VISIBLE : View.GONE);
            mImageView.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

      private void inflateDownloadControls() {
        if (mDownloadButton == null) {
            //inflate the download controls
            findViewById(R.id.mms_downloading_view_stub).setVisibility(VISIBLE);
            mDownloadButton = (Button) findViewById(R.id.btn_download_msg);
            mDownloadingLabel = (TextView) findViewById(R.id.label_downloading);
            mMmsMessageListItemPlugin.initDownloadLayout(ComposeMessageActivity.getComposeContext(),
                (LinearLayout) findViewById(R.id.mms_download_controls),
                (TextView) findViewById(R.id.text_expire));
        }
    }

    private LineHeightSpan mSpan = new LineHeightSpan() {
        @Override
        public void chooseHeight(CharSequence text, int start,
                int end, int spanstartv, int v, FontMetricsInt fm) {
            fm.ascent -= 10;
        }
    };

    TextAppearanceSpan mTextSmallSpan =
        new TextAppearanceSpan(mContext, android.R.style.TextAppearance_Small);

    ForegroundColorSpan mColorSpan = null;  // set in ctor

    private CharSequence formatMessage(MessageItem msgItem, String body,
                                       String subject, Pattern highlight,
                                       String contentType) {
        SpannableStringBuilder buf = new SpannableStringBuilder();
        String langage = Locale.getDefault().getLanguage();//add by lipeng for number display
        boolean hasSubject = !TextUtils.isEmpty(subject);
        if (hasSubject) {
            buf.append(mContext.getResources().getString(R.string.inline_subject, subject));
        }

        if (!TextUtils.isEmpty(body)) {
        	
        	//[add by lizhao for HQ01577414 at 20151231 begain
        	Configuration con = this.getResources().getConfiguration();
    		String l = con.locale.getLanguage();
    		if (l.equals("ar") || l.equals("fa") || l.equals("iw")) {// RTL language
    			Pattern pattern = Pattern.compile("\\+[0-9]+");
    			Matcher mm = pattern.matcher(body);
    			while (mm.find()) {
    				String subunmber = mm.group(0);
    				String newChar = '\u202D' + subunmber + '\u202C';
    				body = body.toString().replace(subunmber, newChar);
    			}
    		}
        	//add by lizhao for HQ01577414 at 20151231 end]
        	
               //add by lipeng for number display
               if(MessageUtils.isInteger(body)&&(langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw"))){
                       body = "\u202D"+body+"\u202C";
               }
               //end by lipeng
            // Converts html to spannable if MmsContentType is "text/html".
            if (contentType != null && MmsContentType.TEXT_HTML.equals(contentType)) {
                buf.append("\n");
                buf.append(Html.fromHtml(body));
            } else {
                if (hasSubject) {
                    buf.append(" - ");
                }
                buf.append(body);
            }
        }

        /// M: add for  OP09 Feature, mms cc
        if (msgItem.mMmsCc != null && MmsConfig.isSupportSendMmsWithCc()) {
            if (hasSubject || !TextUtils.isEmpty(body)) {
                buf.append("\n");
            }
            buf.append(TextUtils.replace(mContext.getResources().getString(R.string.inline_cc),
                    new String[] { "%s" }, new CharSequence[] { msgItem.mMmsCc }));
        }

        if (highlight != null) {
            Matcher m = highlight.matcher(buf.toString());
            while (m.find()) {
                buf.setSpan(new StyleSpan(Typeface.BOLD), m.start(), m.end(), 0);
            }
        }
        /// M: @{
        buf.setSpan(mLeadingMarginSpan, 0, buf.length(), 0);
        /// @}
        return buf;
    }

    private void drawPlaybackButton(MessageItem msgItem) {
        switch (msgItem.mAttachmentType) {
            case WorkingMessage.SLIDESHOW:
            case WorkingMessage.AUDIO:
            case WorkingMessage.VIDEO:
                updateSlideShowButton(msgItem);
                break;
            case WorkingMessage.IMAGE:
                if (msgItem.mSlideshow.get(0).hasText()) {
                    MmsLog.d(TAG, "msgItem is image and text");
                    updateSlideShowButton(msgItem);
                } else {
                    mSlideShowButton.setVisibility(View.GONE);
                }
                break;
            default:
                mSlideShowButton.setVisibility(View.GONE);
                break;
        }
    }

    private void updateSlideShowButton(MessageItem msgItem) {
        // Show the 'Play' button and bind message info on it.
        mSlideShowButton.setTag(msgItem);
        /// M: @{
        mSlideShowButton.setVisibility(View.GONE);
        Bitmap bitmap = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.mms_play_btn);
        if (msgItem.hasDrmContent()) {
            if (FeatureOption.MTK_DRM_APP) {
                MmsLog.i(TAG, " msgItem hasDrmContent");
                Drawable front = mContext.getResources().getDrawable(MessageResource.drawable.drm_red_lock);

                OmaDrmClient drmManager = MmsApp.getApplication().getDrmManagerClient();
                Bitmap drmBitmap = OmaDrmUiUtils.overlayBitmap(drmManager, bitmap, front);
                mSlideShowButton.setImageBitmap(drmBitmap);
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                    bitmap = null;
                }
            } else {
                MmsLog.i(TAG, " msgItem hasn't DrmContent");
                mSlideShowButton.setImageBitmap(bitmap);
            }
        } else {
            MmsLog.i(TAG, " msgItem hasn't DrmContent");
            mSlideShowButton.setImageBitmap(bitmap);
        }
        /// @}
        // Set call-back for the 'Play' button.
        mSlideShowButton.setOnClickListener(this);
        mSlideShowButton.setVisibility(View.VISIBLE);
        setLongClickable(true);

        // When we show the mSlideShowButton, this list item's onItemClickListener doesn't
        // get called. (It gets set in ComposeMessageActivity:
        // mMsgListView.setOnItemClickListener) Here we explicitly set the item's
        // onClickListener. It allows the item to respond to embedded html links and at the
        // same time, allows the slide show play button to work.
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onMessageListItemClick();
            }
        });
    }

    // OnClick Listener for the playback button
    @Override
    public void onClick(View v) {
        /// M: add for multi-delete @{
        if (mSelectedBox != null && mSelectedBox.getVisibility() == View.VISIBLE) {
            return;
        }
        if (!sImageButtonCanClick) {
            return;
        }
        sImageButtonCanClick = false;
        /// @}
        sendMessage(mMessageItem, MSG_LIST_PLAY);
        /// M:
        if (mHandler != null) {
            Runnable run = new Runnable() {
                public void run() {
                    sImageButtonCanClick = true;
                }
            };
            mHandler.postDelayed(run, 1000);
        }
    }

    private void sendMessage(MessageItem messageItem, int message) {
        if (mHandler != null) {
            Message msg = Message.obtain(mHandler, message);
            msg.obj = messageItem;
            msg.sendToTarget(); // See ComposeMessageActivity.mMessageListItemHandler.handleMessage
        }
    }

    public void onMessageListItemClick() {
        if (mMessageItem == null) {
            MmsLog.e(TAG, "onMessageListItemClick(): Message Item is null!");
            return;
        }
        if (!sClickCanResponse) {
            MmsLog.e(TAG, "onMessageListItemClick(): sClickCanResponse is false!");
            return;
        }

        MmsLog.d(IPMSG_TAG, "onMessageListItemClick(): msgId = " + mMessageItem.mMsgId + ", ipmsg_id = "
            + mMessageItem.mIpMessageId);
        /// M: add for multi-delete @{
        if (mSelectedBox != null && mSelectedBox.getVisibility() == View.VISIBLE) {
            if (!mSelectedBox.isChecked()) {
                setSelectedBackGroud(true);
            } else {
                setSelectedBackGroud(false);
            }
            if (null != mHandler) {
                Message msg = Message.obtain(mHandler, ITEM_CLICK);
                msg.arg1 = (int) (mMessageItem.mType.equals("mms") ? -mMessageItem.mMsgId : mMessageItem.mMsgId);
                msg.arg2 = mMessageItem.mLocked ?
                    MultiDeleteActivity.MESSAGE_STATUS_LOCK : MultiDeleteActivity.MESSAGE_STATUS_NOT_LOCK;
                msg.obj = mMessageItem;
                msg.sendToTarget();
            }
            return;
        }
        /// @}

        // If the message is a failed one, clicking it should reload it in the compose view,
        // regardless of whether it has links in it
        if (mMessageItem != null &&
                ((mMessageItem.isOutgoingMessage() &&
                mMessageItem.isFailedMessage()) ||
                mMessageItem.mDeliveryStatus == MessageItem.DeliveryStatus.FAILED)) {
            /// M: add for ipmessage
            if (mMessageItem.mIpMessageId > 0) {
                if (mHandler != null) {
                    final int what;
                    what = MSG_LIST_RESEND_IPMSG;
                    Message msg = Message.obtain(mHandler, what);
                    Bundle bundle = new Bundle();
                    bundle.putLong("MSG_ID", mMessageItem.mMsgId);
                    bundle.putInt("SUB_ID", mMessageItem.mSubId);
                    msg.setData(bundle);
                    msg.obj = mMessageItem;
                    msg.sendToTarget(); // See ComposeMessageActivity.mMessageListItemHandler.handleMessage
                }
            } else {
                /// M: add for OP09 feature @{
                if (!mMmsMessageListItemPlugin.needEditFailedMessge(mContext, mMessageItem.mMsgId,
                    mMessageItem.mIpMessageId)) {
                    if (mHandler != null) {
                        Message msg = Message.obtain(mHandler, IMmsComposeExt.MSG_LIST_SHOW_MSGITEM_DETAIL);
                        msg.obj = mMessageItem;
                        msg.sendToTarget(); // See ComposeMessageActivity.mMessageListItemHandler.handleMessage
                    }
                    return;
                }
                /// @}
                // Assuming the current message is a failed one, reload it into the compose view so
                // the user can resend it.
                sendMessage(mMessageItem, MSG_LIST_EDIT);
            }
            return;
        }
        /// M: add for ipmessage
        if (MmsConfig.isServiceEnabled(mContext) && mMessageItem.mIpMessageId > 0) {
            if (mMessageItem.mIpMessage == null) {
                mMessageItem.mIpMessage = IpMessageUtils.getMessageManager(mContext).getIpMsgInfo(mMessageItem.mMsgId);
            }
            if (null != mMessageItem.mIpMessage) {
                if (mMessageItem.mIpMessage.getType() != IpMessageType.TEXT) {
                    MmsLog.d(IPMSG_TAG, "onMessageListItemClick(): open IP message media. msgId = " + mMessageItem.mMsgId);
                    openMedia(mMessageItem.mIpMessage, mMessageItem.mMsgId);
                    return;
                }
            }
        }

        // Check for links. If none, do nothing; if 1, open it; if >1, ask user to pick one
        final URLSpan[] spans = mBodyTextView.getUrls();
        /// M: @{
        final java.util.ArrayList<String> urls = MessageUtils.extractUris(spans);
        final String telPrefix = "tel:";
        String url = "";
        boolean isTel = false;
        /// M: fix bug ALPS00367589, uri_size sync according to urls after filter to unique array @{
        for (int i = 0; i < urls.size(); i++) {
            url = urls.get(i);
            if (url.startsWith(telPrefix)) {
                isTel = true;
                if (MmsConfig.isSmsEnabled(mContext)) {
                    urls.add("smsto:" + url.substring(telPrefix.length()));
                }
            }
        }
        /// @}
        sClickCanResponse = false;
        if (spans.length == 0) {
            sendMessage(mMessageItem, MSG_LIST_DETAILS);    // show the message details dialog
        /// M: @{
        //} else if (spans.length == 1) {
        } else if (spans.length == 1 && !isTel) {
        /// @}
            /*
            Uri uri = Uri.parse(spans[0].getURL());
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, mContext.getPackageName());
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
            mContext.startActivity(intent);
            */
            final String mUriTemp = spans[0].getURL();

            /// M: New plugin API @{
            MmsConfig.openUrl(mContext, mUriTemp);
            /// @}
        /** M: delete google default code @{
        } else if (spans.length == 1) {
            spans[0].onClick(mBodyTextView);
        */
        }  else {
            /// M: modify google default Code.@{
            // ArrayAdapter<URLSpan> adapter =
            //      new ArrayAdapter<URLSpan>(mContext, android.R.layout.select_dialog_item, spans) {

            ArrayAdapter<String> adapter =
                new ArrayAdapter<String>(mContext, android.R.layout.select_dialog_item, urls) {
              /// @}
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View v = super.getView(position, convertView, parent);
                    TextView tv = (TextView) v;
                    /// M: move this try into the exact place
                    //try {
                        /// M: modify google default Code @{
                        // URLSpan span = getItem(position);
                        // String url = span.getURL();
                        String url = getItem(position).toString();
                        /// @}
                        Uri uri = Uri.parse(url);

                        final String telPrefix = "tel:";
                        /// M: use default icon to display
                        Drawable d = parseAppIcon(mContext, url);
						//modify by lipeng 
						String locale = Locale.getDefault().getLanguage();
                        if("fa".equals(locale) || "ar".equals(locale)|| "iw".equals(locale)){
	                        if (d != null) {
	                            d.setBounds(0, 0, d.getIntrinsicHeight(), d.getIntrinsicHeight());
	                            tv.setCompoundDrawablePadding(13);
	                            tv.setCompoundDrawables(null, null, d, null);
								tv.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
	                        } else {
	                           /// M: currently we only know this one
	                           if (url.startsWith(telPrefix)) {
	                              d = mContext.getResources().getDrawable(R.drawable.ic_launcher_phone);
	                              d.setBounds(0, 0, d.getIntrinsicHeight(), d.getIntrinsicHeight());
	                              tv.setCompoundDrawablePadding(13);
	                                tv.setCompoundDrawables(null, null, d, null);
	                            } else {
	                                tv.setCompoundDrawables(null, null, null, null);
	                            }
	                        }
                        }else{
	                        if (d != null) {
	                            d.setBounds(0, 0, d.getIntrinsicHeight(), d.getIntrinsicHeight());
	                            tv.setCompoundDrawablePadding(10);
	                            tv.setCompoundDrawables(d, null, null, null);
	                        } else {
	                            /// M: currently we only know this one
	                            if (url.startsWith(telPrefix)) {
	                                d = mContext.getResources().getDrawable(R.drawable.ic_launcher_phone);
	                                d.setBounds(0, 0, d.getIntrinsicHeight(), d.getIntrinsicHeight());
	                                tv.setCompoundDrawablePadding(10);
	                                tv.setCompoundDrawables(d, null, null, null);
	                            } else {
	                                tv.setCompoundDrawables(null, null, null, null);
	                            }
	                        }
                        }//end lipeng

                        /// M: @{
                        final String smsPrefix = "smsto:";
                        final String mailPrefix = "mailto";
                        /// @}
                        if (url.startsWith(telPrefix)) {
                            url = PhoneNumberUtils.formatNumber(
                                            url.substring(telPrefix.length()), mDefaultCountryIso);
                            if (url == null) {
                                MmsLog.w(TAG, "url turn to null after calling PhoneNumberUtils.formatNumber");
                                url = getItem(position).toString().substring(telPrefix.length());
                            }
                        } else if (url.startsWith(smsPrefix)) { /// M: @{
                            url = PhoneNumberUtils.formatNumber(
                                            url.substring(smsPrefix.length()), mDefaultCountryIso);
                            if (url == null) {
                                MmsLog.w(TAG, "url turn to null after calling PhoneNumberUtils.formatNumber");
                                url = getItem(position).toString().substring(smsPrefix.length());
                            }
                        } else if (url.startsWith(mailPrefix)) {
                            String uu = url.substring(mailPrefix.length() + 1, url.length());
                            uu = Uri.encode(uu);
                            uu = mailPrefix + ":" + uu;
                            MailTo mt = MailTo.parse(uu);
                            url = mt.getTo();
                        }
                        /// @}
                    final String langage = Locale.getDefault().getLanguage();//add by lipeng for number display
					if (langage.startsWith("ar")
							|| langage.startsWith("fa")
							|| langage.startsWith("iw")) {
						tv.setText("\u202D" + url + "\u202C");
					} else {
						tv.setText(url);
					}

                    /// M: move this catch to the exact place
                    //} catch (android.content.pm.PackageManager.NameNotFoundException ex) {
                        // it's ok if we're unable to set the drawable for this view - the user
                        // can still use it
                    //    tv.setCompoundDrawables(null, null, null, null);
                    //    return v;
                    //}
                    return v;
                }
            };

            AlertDialog.Builder b = new AlertDialog.Builder(mContext);

            DialogInterface.OnClickListener click = new DialogInterface.OnClickListener() {
                @Override
                public final void onClick(DialogInterface dialog, int which) {
                    if (which >= 0) {
                        /// M: change google default action to choose how to response click  @{
                        //spans[which].onClick(mBodyTextView);

                        Uri uri = Uri.parse(urls.get(which));
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        intent.putExtra(Browser.EXTRA_APPLICATION_ID, mContext.getPackageName());
                        if (urls.get(which).startsWith("smsto:")) {
                            intent.setClassName(mContext, "com.android.mms.ui.SendMessageToActivity");
                        }
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                        mContext.startActivity(intent);
                        if (urls.get(which).startsWith("smsto:")) {
                            intent.setClassName(mContext, "com.android.mms.ui.SendMessageToActivity");
                        }
                        /// @}

                    }
                    dialog.dismiss();
                }
            };

            b.setTitle(R.string.select_link_title);
            b.setCancelable(true);
            b.setAdapter(adapter, click);

            b.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public final void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            da=b.create();
            b.show();
        }
    }
    public  static AlertDialog  getAlertDialog() {
		return da;
	}
    
   private void setOnClickListener(final MessageItem msgItem) {
        switch(msgItem.mAttachmentType) {
            case WorkingMessage.IMAGE:
            case WorkingMessage.VIDEO:
	/**add by liruihong for HQ01432523 begin*/
	     case WorkingMessage.AUDIO:
	/**add by liruihong for HQ01432523 end*/
                mImageView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sendMessage(msgItem, MSG_LIST_PLAY);
                    }
                });
                mImageView.setOnLongClickListener(new OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        return v.showContextMenu();
                    }
                });
                break;

            default:
                mImageView.setOnClickListener(null);
                break;
            }
    }

    private void setMediaOnClickListener(final MessageItem msgItem) {
        switch(msgItem.mAttachmentType) {
        case WorkingMessage.IMAGE:
        case WorkingMessage.VIDEO:
	/**add by liruihong for HQ01432523 begin*/
	 case WorkingMessage.AUDIO:
	/**add by liruihong for HQ01432523 end*/
            mImageView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    /// M: @{
                    //add for multi-delete
                    if (mSelectedBox != null && mSelectedBox.getVisibility() == View.VISIBLE) {
                        mSelectedBox.setChecked(!mSelectedBox.isChecked());

                        if (mSelectedBox.isChecked()) {
                            setSelectedBackGroud(true);
                        } else {
                            setSelectedBackGroud(false);
                        }

                        if (null != mHandler) {
                            Message msg = Message.obtain(mHandler, ITEM_CLICK);
                            msg.arg1 = (int) (mMessageItem.mType.equals("mms") ? -mMessageItem.mMsgId : mMessageItem.mMsgId);
                            msg.arg2 = mMessageItem.mLocked ? MultiDeleteActivity.MESSAGE_STATUS_LOCK
                                        : MultiDeleteActivity.MESSAGE_STATUS_NOT_LOCK;
                            msg.obj = mMessageItem;
                            msg.sendToTarget();
                        }
                        return;
                    }
                    /// @}
                    if (!sImageButtonCanClick) {
                        return;
                    }
                    sImageButtonCanClick = false;
                    /// M: @{
                    if (msgItem.mAttachmentType == WorkingMessage.IMAGE && msgItem.mSlideshow.get(0).hasText()) {
                        mImageView.setOnClickListener(null);
                    } else {
                        sendMessage(msgItem, MSG_LIST_PLAY);
                    }
                    if (mHandler != null) {
                        Runnable run = new Runnable() {
                            public void run() {
                                sImageButtonCanClick = true;
                            }
                        };
                        mHandler.postDelayed(run, 1000);
                    }
                    /// @}
                }
            });
            mImageView.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return v.showContextMenu();
                }
            });
            break;

        default:
            mImageView.setOnClickListener(null);
            break;
        }
    }


    /**
     * Assuming the current message is a failed one, reload it into the compose view so that the
     * user can resend it.
     */
    private void recomposeFailedMessage() {
        String type = mMessageItem.mType;
        final int what;
        /// M:
        if (mMessageItem.mIpMessageId > 0) {
            what = MSG_LIST_RESEND_IPMSG;
        } else if (type.equals("sms")) {
            what = MSG_LIST_EDIT_SMS;
        } else {
            what = MSG_LIST_EDIT_MMS;
        }
        /// M: @{
        //add for multi-delete
        if (mSelectedBox != null && mSelectedBox.getVisibility() == View.VISIBLE) {
            return;
        }
        /// @}
        if (mHandlerTime != 0) {
            long currentTime = System.currentTimeMillis();
            MmsLog.d(M_TAG, "recomposeFailedMessage(): coming one click. currentTime=" + currentTime + ", mHandlerTime="
                    + mHandlerTime);
            MmsLog.d(M_TAG, "recomposeFailedMessage(): currentTime - mHandlerTime=" + (currentTime - mHandlerTime));
            if ((currentTime - mHandlerTime) < 1000) {
                MmsLog.d(M_TAG, "recomposeFailedMessage(): cancel one click");
                mHandlerTime = currentTime;
                return;
            }
        }
        mHandlerTime = System.currentTimeMillis();

        if (null != mHandler) {
            MmsLog.d(M_TAG, "recomposeFailedMessage(): sending one message");
            Message msg = Message.obtain(mHandler, what);
            msg.obj = mMessageItem;
            /// M:
            if (mMessageItem.mIpMessageId > 0) {
                Bundle bundle = new Bundle();
                bundle.putLong("MSG_ID", mMessageItem.mMsgId);
                bundle.putInt("SUB_ID", mMessageItem.mSubId);
                msg.setData(bundle);
            }
            msg.sendToTarget();
        }
    }

    private void drawRightStatusIndicator(MessageItem msgItem) {
        // Locked icon
        if (msgItem.mLocked) {
            mLockedIndicator.setImageResource(R.drawable.ic_lock_message_sms);
            mLockedIndicator.setVisibility(View.VISIBLE);
            mSubStatus.setPadding(PADDING_LEFT_THR, 0, 0, 0);
        } else {
            mLockedIndicator.setVisibility(View.GONE);
            mSubStatus.setPadding(PADDING_LEFT_TWE, 0, 0, 0);
        }
        // Delivery icon - we can show a failed icon for both sms and mms, but for an actual
        // delivery, we only show the icon for sms. We don't have the information here in mms to
        // know whether the message has been delivered. For mms, msgItem.mDeliveryStatus set
        // to MessageItem.DeliveryStatus.RECEIVED simply means the setting requesting a
        // delivery report was turned on when the message was sent. Yes, it's confusing!
        /// M: add for ip message, message status
        if (IpMessageUtils.getIpMessagePlugin(mContext).isActualPlugin() && msgItem.mIpMessageId > 0
                && !msgItem.isSubMsg()) {
            int ipMsgStatus = msgItem.mIpMessage.getStatus();
            int statusResId = IpMessageUtils.getIpMessageStatusResourceId(ipMsgStatus);
            if (statusResId != 0) {
				/*HQ_zhangjing add for al812 mms ui */
                mDeliveredIndicator.setVisibility(View.VISIBLE);
                setSendStatusText(statusResId);/*HQ_zhangjing 2015-10-26 modified for CQ  HQ01456345*/
                if (ipMsgStatus == IpMessageStatus.NOT_DELIVERED || ipMsgStatus == IpMessageStatus.FAILED) {
                    mDeliveredIndicator.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            recomposeFailedMessage();
                        }
                    });
                }
            } else {
                mDeliveredIndicator.setVisibility(View.GONE);
            }
        } else if ((msgItem.isOutgoingMessage() && msgItem.isFailedMessage()) ||
                msgItem.mDeliveryStatus == MessageItem.DeliveryStatus.FAILED) {
			/*HQ_zhangjing add for al812 mms ui */
            mDeliveredIndicator.setVisibility(View.VISIBLE);
            setSendStatusText(R.drawable.ic_list_alert_sms_failed);/*HQ_zhangjing 2015-10-26 modified for CQ  HQ01456345*/
        } else if (msgItem.isSms() &&
                msgItem.mDeliveryStatus == MessageItem.DeliveryStatus.RECEIVED) {
            /// M: @{
            mDeliveredIndicator.setClickable(false);
            /// @}
            ///M: modified for new icon
//            mDeliveredIndicator.setImageResource(R.drawable.ic_sms_mms_delivered);
			/*HQ_zhangjing add for al812 mms ui */
            mDeliveredIndicator.setVisibility(View.VISIBLE);
            setSendStatusText(R.drawable.im_meg_status_reach);/*HQ_zhangjing 2015-10-26 modified for CQ  HQ01456345*/
        } else {
            /// M: Add new status icon for MMS or SMS. @{
            int resId = MessageUtils.getStatusResourceId(mContext, msgItem);
            if (resId > 0) {
                mDeliveredIndicator.setClickable(false);
				/*HQ_zhangjing add for al812 mms ui */
                mDeliveredIndicator.setVisibility(View.VISIBLE);
                setSendStatusText(resId);/*HQ_zhangjing 2015-10-26 modified for CQ  HQ01456345*/
            } else {
                mDeliveredIndicator.setVisibility(View.GONE);
            }
            /// @}
        }
        /// M: add for OP09 @{
        mMmsMessageListItemPlugin.drawMassTextMsgStatus(this.getContext(),
            (LinearLayout) findViewById(R.id.status_panel), msgItem.isSms(), msgItem.mIpMessageId);
        /// @}
        // Message details icon - this icon is shown both for sms and mms messages. For mms,
        // we show the icon if the read report or delivery report setting was set when the
        // message was sent. Showing the icon tells the user there's more information
        // by selecting the "View report" menu.
        if (msgItem.mDeliveryStatus == MessageItem.DeliveryStatus.INFO || msgItem.mReadReport
                || (msgItem.isMms() &&
                        msgItem.mDeliveryStatus == MessageItem.DeliveryStatus.RECEIVED)) {
            mDetailsIndicator.setImageResource(R.drawable.ic_sms_mms_details);
            mDetailsIndicator.setVisibility(View.VISIBLE);
        } else {
            mDetailsIndicator.setVisibility(View.GONE);
        }
        /// M: Add for OP09 @{
        MmsLog.d(TAG, "MessageListItem SubId:" + msgItem.mSubId);
		/*HQ_zhangjing 2015-10-24 modified for CQ  HQ01459241 begien */
        /*if (!msgItem.isSubMsg()) {
            mMmsMessageListItemPlugin.showSimType(this.getContext(), msgItem.mSubId, mSubStatus);
        }*/
        /*if (MmsConfig.isShowDualTimeForMsgItemEnable() && !msgItem.isSubMsg()) {
            mMmsMessageListItemPlugin.setDualTime(this.getContext(), msgItem.isReceivedMessage(),
                    msgItem.mSubId,
                (TextView) findViewById(R.id.send_time_txt), (LinearLayout) findViewById(R.id.double_time_layout),
                buildTimestampLine(msgItem.mTimestamp));
        }*/
		/*HQ_zhangjing 2015-10-24 modified for CQ  HQ01459241 end*/
        /// @}
    }

    @Override
    public void setImageRegionFit(String fit) {
        // TODO Auto-generated method stub
    }

    @Override
    public void setImageVisibility(boolean visible) {
        // TODO Auto-generated method stub
    }

    @Override
    public void setText(String name, String text) {
        // TODO Auto-generated method stub
    }

    @Override
    public void setTextVisibility(boolean visible) {
        // TODO Auto-generated method stub
    }

    @Override
    public void setVideo(String name, Uri uri) {
    }

    @Override
    public void setVideoThumbnail(String name, Bitmap bitmap) {
        showMmsView(true);

        try {
            mImageView.setImageBitmap(bitmap);
            mImageView.setVisibility(VISIBLE);
        } catch (java.lang.OutOfMemoryError e) {
            Log.e(TAG, "setVideo: out of memory: ", e);
        }
    }

    @Override
    public void setVideoVisibility(boolean visible) {
        // TODO Auto-generated method stub
    }

    @Override
    public void stopAudio() {
        // TODO Auto-generated method stub
    }

    @Override
    public void stopVideo() {
        // TODO Auto-generated method stub
    }

    @Override
    public void reset() {
    }

    @Override
    public void setVisibility(boolean visible) {
        // TODO Auto-generated method stub
    }

    @Override
    public void pauseAudio() {
        // TODO Auto-generated method stub

    }

    @Override
    public void pauseVideo() {
        // TODO Auto-generated method stub

    }

    @Override
    public void seekAudio(int seekTo) {
        // TODO Auto-generated method stub

    }

    @Override
    public void seekVideo(int seekTo) {
        // TODO Auto-generated method stub

    }

    /**
     * Override dispatchDraw so that we can put our own background and border in.
     * This is all complexity to support a shared border from one item to the next.
     */
    /*** M: remove Google default code
    @Override
    public void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);

        // This custom border is causing our scrolling fps to drop from 60+ to the mid 40's.
        // Commenting out for now until we come up with a new UI design that doesn't require
        // the border.
        return;

//        View v = mMessageBlock;
//        int selectBoxWidth = 0;
//        if (mSelectedBox != null && mSelectedBox.getVisibility() == View.VISIBLE) {
//            selectBoxWidth = mSelectedBox.getWidth();
//        }
//        if (v != null) {
//            float l = v.getX() + selectBoxWidth;
//            float t = v.getY();
//            float r = v.getX() + v.getWidth() + selectBoxWidth;
//            float b = v.getY() + v.getHeight();
//
//            Path path = mPath;
//            path.reset();
//
//            super.dispatchDraw(c);
//
//            path.reset();
//
//            r -= 1;
//
//            // This block of code draws the border around the "message block" section
//            // of the layout.  This would normally be a simple rectangle but we omit
//            // the border at the point of the avatar's divot.  Also, the bottom is drawn
//            // 1 pixel below our own bounds to get it to line up with the border of
//            // the next item.
//            //
//            // But for the last item we draw the bottom in our own bounds -- so it will
//            // show up.
//            if (mIsLastItemInList) {
//                b -= 1;
//            }
//            if (mAvatar.getPosition() == Divot.RIGHT_UPPER) {
//                path.moveTo(l, t + mAvatar.getCloseOffset());
//                path.lineTo(l, t);
//                if (selectBoxWidth > 0) {
//                    path.lineTo(l - mAvatar.getWidth() - selectBoxWidth, t);
//                }
//                path.lineTo(r, t);
//                path.lineTo(r, b);
//                path.lineTo(l, b);
//                path.lineTo(l, t + mAvatar.getFarOffset());
//            } else if (mAvatar.getPosition() == Divot.LEFT_UPPER) {
//                path.moveTo(r, t + mAvatar.getCloseOffset());
//                path.lineTo(r, t);
//                path.lineTo(l - selectBoxWidth, t);
//                path.lineTo(l - selectBoxWidth, b);
//                path.lineTo(r, b);
//                path.lineTo(r, t + mAvatar.getFarOffset());
//            }
//
//            Paint paint = mPaint;
////            paint.setColor(0xff00ff00);
//            paint.setColor(0xffcccccc);
//            paint.setStrokeWidth(1F);
//            paint.setStyle(Paint.Style.STROKE);
//            c.drawPath(path, paint);
//        } else {
//            super.dispatchDraw(c);
//        }
    }
*/

    /// M: @{
    static final int ITEM_CLICK          = 5;
    static final int ITEM_MARGIN         = 50;
    private TextView mSubStatus;
    public CheckBox mSelectedBox;
	private ImageView mSimIndicator;//HQ_zhangjing add for al812 mms ui

    private CharSequence formatTimestamp(MessageItem msgItem, String timestamp) {
        SpannableStringBuilder buf = new SpannableStringBuilder();
        if (msgItem.isSending()) {
            timestamp = mContext.getResources().getString(R.string.sending_message);
        }

           buf.append(TextUtils.isEmpty(timestamp) ? " " : timestamp);
           buf.setSpan(mSpan, 1, buf.length(), 0);

        //buf.setSpan(mTextSmallSpan, 0, buf.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        // Make the timestamp text not as dark
        buf.setSpan(mColorSpan, 0, buf.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return buf;
    }

//    private CharSequence formatSubStatus(MessageItem msgItem) {
//        SpannableStringBuilder buffer = new SpannableStringBuilder();
//        // If we're in the process of sending a message (i.e. pending), then we show a "Sending..."
//        // string in place of the timestamp.
//        //Add sub info
//        CharSequence subInfo = MessageUtils.getSubInfo(mContext, msgItem.mSubId);
//        Log.i(TAG,"formatSubStatus::subInfo="+subInfo);
//        if (subInfo.length() > 0) {
//            buffer.append(subInfo);
//        }
//        int subInfoStart = buffer.length();
//
//        //buffer.setSpan(mTextSmallSpan, 0, buffer.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//        // Make the timestamp text not as dark
//        buffer.setSpan(mColorSpan, 0, subInfoStart, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//
//        return buffer;
//    }

    public void setSelectedBackGroud(boolean selected) {
        if (selected) {
            mSelectedBox.setChecked(true);
//            mSelectedBox.setBackgroundDrawable(null);
//            mMessageBlock.setBackgroundDrawable(null);
//            mDateView.setBackgroundDrawable(null);
            setBackgroundResource(R.drawable.list_selected_holo_light);
        } else {
            setBackgroundDrawable(null);
            mSelectedBox.setChecked(false);
//            mSelectedBox.setBackgroundResource(R.drawable.listitem_background);
//            mMessageBlock.setBackgroundResource(R.drawable.listitem_background);
//            mDateView.setBackgroundResource(R.drawable.listitem_background);
        }
    }

    public void bindDefault(MessageItem msgItem, boolean isLastItem) {
        MmsLog.d(M_TAG, "bindDefault()");
        mIsLastItemInList = isLastItem;
        mSelectedBox.setVisibility(View.GONE);
        setLongClickable(false);
        setFocusable(false);
        setClickable(false);
        String msgType = "";
        if (msgItem != null) {
            msgType = msgItem.mType;
        }
        if (mMmsView != null) {
            mMmsView.setVisibility(View.GONE);
        }
        if (mFileAttachmentView != null) {
            mFileAttachmentView.setVisibility(View.GONE);
        }
        /// M: fix bug ALPS00435619, show Refreshing string
        /// when we fail to reload item and put in cache during 500 ms
        mBodyTextView.setVisibility(View.VISIBLE);
        // M:for ALPS01065027,just for compose sms messagelist use in scrolling
        if ("sms".equals(msgType)) {
            CharSequence formattedMessage = formatMessage(msgItem, msgItem.mBody, msgItem.mSubject,
                    msgItem.mHighlight, msgItem.mTextContentType);
            msgItem.setCachedFormattedMessage(formattedMessage);

            if (TextUtils.isEmpty(msgItem.mBody) && TextUtils.isEmpty(msgItem.mSubject)) {
                mBodyTextView.setVisibility(View.GONE);
            } else {
                mBodyTextView.setText(formattedMessage);
            }
        } else {
            mBodyTextView.setText(R.string.refreshing);
        }

        // if (null != mTimeDivider) {
        // mTimeDivider.setVisibility(View.GONE);
        // }
        //
        // if (null != mUnreadDivider) {
        // mUnreadDivider.setVisibility(View.GONE);
        // }

        // mDateView.setVisibility(View.GONE);
        // mSimStatus.setVisibility(View.GONE);
        if (mDownloadButton != null) {
            mDownloadingLabel.setVisibility(View.GONE);
            mDownloadButton.setVisibility(View.GONE);
            /// M: add for OP09 @{
            mMmsMessageListItemPlugin.hideAllButton();
            /// @}
        }
        mLockedIndicator.setVisibility(View.GONE);
        mSubStatus.setPadding(PADDING_LEFT_TWE, 0, 0, 0);
//        CharSequence formattedSubStatus = "";
        if (msgItem != null) {
            MmsLog.e(TAG, "message item is not null");
//            formattedSubStatus = msgItem.getCachedFormattedSubStatus();
            //if (!msgItem.isSubMsg() && !TextUtils.isEmpty(formattedSubStatus)) {
            if (!msgItem.isSubMsg()) {
                mSubStatus.setVisibility(View.VISIBLE);
                //mSubStatus.setText(formattedSubStatus);
                setSubIconAndLabel(null);
            }
        } else {
            MmsLog.e(TAG, "message item is null");
        }
        // mDeliveredIndicator.setVisibility(View.GONE);
        mDetailsIndicator.setVisibility(View.GONE);
        /// M: fix bug ALPS00432063, check NPE
        if (mAvatar != null && mAvatar.getVisibility() == View.VISIBLE) {
            mAvatar.setImageDrawable(sDefaultContactImage);
        }

        /// M: fix bug ALPS00439894, MTK MR1 new feature: Group Mms
        /// set Gone in bindDefault()
        if (mMessageItem != null && !mMessageItem.isMe()) {
            mSenderName = (TextView) findViewById(R.id.sender_name);
            mSendNameSeparator = findViewById(R.id.sender_name_separator);
            mSenderPhoto = (QuickContactBadge) findViewById(R.id.sender_photo);
            if (mSenderName != null && mSenderPhoto != null && mSendNameSeparator != null) {
                mSenderName.setVisibility(View.GONE);
                mSendNameSeparator.setVisibility(View.GONE);
                mSenderPhoto.setVisibility(View.GONE);
            }
        }
        /// M: add for ip message
        if (mIpImageView != null) {
            mIpImageView.setVisibility(View.GONE);
        }
        if (mCaption != null) {
            mCaption.setVisibility(View.GONE);
        }
        if (mIpAudioView != null) {
            mIpAudioView.setVisibility(View.GONE);
        }
        if (mIpVCardView != null) {
            mIpVCardView.setVisibility(View.GONE);
        }
        if (mIpVCalendarView != null) {
            mIpVCalendarView.setVisibility(View.GONE);
        }
        if (mOnLineDivider != null) {
            mOnLineDivider.setVisibility(View.GONE);
        }

        /// M: hide file transfer view
        if (mIpmsgFileDownloadContrller != null) {
            mIpmsgFileDownloadContrller.setVisibility(View.GONE);
        }
        if (mIpmsgFileDownloadView != null) {
            mIpmsgFileDownloadView.setVisibility(View.GONE);
        }
        /// M: add for ALPS01918645, hide deliver status and time because they contains old data.
        if (mDeliveredIndicator != null) {
            mDeliveredIndicator.setVisibility(View.GONE);
        }
        if (mDateView != null) {
            mDateView.setVisibility(View.GONE);
        }
        /// @}
        requestLayout();
    }
    /// @}

    ///M: add for adjust text size
    public void setBodyTextSize(float size) {
        if (mBodyTextView != null && mBodyTextView.getVisibility() == View.VISIBLE) {
            mBodyTextView.setTextSize(size);
        }
        if (mCaption != null && mCaption.getVisibility() == View.VISIBLE) {
            mCaption.setTextSize(size);
        }
    }

    @Override
    public void setImage(Uri mUri) {
        try {
            Bitmap bitmap = null;
            if (null == mUri) {
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_missing_thumbnail_picture);
            } else {
                InputStream mInputStream = null;
                try {
                    mInputStream = this.getContext().getContentResolver().openInputStream(mUri);
                    if (mInputStream != null) {
                        bitmap = BitmapFactory.decodeStream(mInputStream);
                    }
                } catch (FileNotFoundException e) {
                    bitmap = null;
                } finally {
                    if (mInputStream != null) {
                        mInputStream.close();
                    }
                }
                setImage("", bitmap);
            }
        } catch (java.lang.OutOfMemoryError e) {
            Log.e(TAG, "setImage(Uri): out of memory: ", e);
        } catch (IOException e) {
            Log.e(TAG, "mInputStream.close() IOException." + e);
        }
    }

    // Add for vCard begin
    private void hideFileAttachmentViewIfNeeded() {
        if (mFileAttachmentView != null) {
            mFileAttachmentView.setVisibility(View.GONE);
        }
    }

    private void importVCard(FileAttachmentModel attach) {
        final String[] filenames = mContext.fileList();
        for (String file : filenames) {
            if (file.endsWith(".vcf")) {
                mContext.deleteFile(file);
            }
        }
        try {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = mContext.getContentResolver().openInputStream(attach.getUri());
                out = mContext.openFileOutput(attach.getSrc(), Context.MODE_WORLD_READABLE);
                byte[] buf = new byte[8096];
                int seg = 0;
                while ((seg = in.read(buf)) != -1) {
                    out.write(buf, 0, seg);
                }
            } finally {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            }
        } catch (FileNotFoundException e) {
            MmsLog.e(TAG, "importVCard, file not found " + attach + ", exception ", e);
        } catch (IOException e) {
            MmsLog.e(TAG, "importVCard, ioexception " + attach + ", exception ", e);
        }
        final File tempVCard = mContext.getFileStreamPath(attach.getSrc());
        if (!tempVCard.exists() || tempVCard.length() <= 0) {
            MmsLog.e(TAG, "importVCard, file is not exists or empty " + tempVCard);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(tempVCard), attach.getContentType().toLowerCase());
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        mContext.startActivity(intent);
    }

    private void showFileAttachmentView(ArrayList<FileAttachmentModel> files) {
        final boolean isCtFeature = MmsConfig.isSupportVCardPreview();
        // There should be one and only one file
        if (files == null || files.size() < 1) {
            Log.e(TAG, "showFileAttachmentView, oops no attachment files found");
            return;
        }
        final int filesize = files.size();
        ArrayList<FileAttachmentModel> mfiles = files;

        if (mFileAttachmentView == null) {
            findViewById(R.id.mms_file_attachment_view_stub).setVisibility(VISIBLE);
            mFileAttachmentView = findViewById(R.id.file_attachment_view);
        }
        mFileAttachmentView.setVisibility(View.VISIBLE);
        /// M: For op09 VCard; @{
        if (isCtFeature) {
            mFileAttachmentView.setBackgroundDrawable(null);
        }
        /// @}
        final FileAttachmentModel attach = files.get(0);
        mFileAttachmentView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mSelectedBox != null && mSelectedBox.getVisibility() == View.VISIBLE) {
                    return;
                }
                if (attach.isVCard()) {
                    /// M: Modify for OP09. @{
                    if (isCtFeature) {
                        try {
                            Intent intent = new Intent(mContext, VCardViewerActivity.class);
                            intent.setData(attach.getUri());
                            intent.putExtra("file_name", attach.getSrc());
                            mContext.startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            MmsLog.e(TAG, "no activity handle ", e);
                        }
                    } else {
                        VCardUtils.importVCard(mContext, attach);
                    }
                    /// @}
                } else if (attach.isVCalendar()) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(attach.getUri(), attach.getContentType().toLowerCase());
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        mContext.startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        MmsLog.e(TAG, "no activity handle ", e);
                    }
                }
            }
        });

        /// M: add for attachment enhance @{
        int attachSize = 0;
        /// M: get external attachment size @{
        for (int i = 0; i < filesize; i++) {
            attachSize += files.get(i).getAttachSize();
        }

        if (MmsConfig.isSupportAttachEnhance()) {
            if (filesize > 1 || (filesize == 1 && !attach.isVCard() && !attach.isVCalendar())) {
                View fileAttachmentView = findViewById(R.id.file_attachment_view);
                fileAttachmentView.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        AlertDialog.Builder b = new AlertDialog.Builder(mContext);
                        b.setTitle(R.string.save_attachment);

                        if (filesize == 1 && !attach.isSupportFormat()) {
                            b.setMessage(R.string.save_single_attachment_notes);
                        } else if (filesize > 1) {
                            b.setMessage(R.string.save_multi_attachment_notes);
                        } else {
                            b.setMessage(R.string.save_single_supportformat_attachment_notes);
                        }
                        ///M: Modify for ALPS01485146
                                                final long iMsgId = mMessageItem.mMsgId;
                        b.setCancelable(true);
                        b.setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public final void onClick(DialogInterface dialog, int which) {
                                    if (!Environment.getExternalStorageState().equals(
                                            Environment.MEDIA_MOUNTED)) {
                                        Toast.makeText(mContext,
                                            R.string.invalid_contact_message, Toast.LENGTH_SHORT);
                                        }

                                        if (filesize == 1) {
                                            boolean succeeded = false;
                                            succeeded = copyTextSingleAttachment(iMsgId);
                                            if (!succeeded) {
                                                succeeded = copySingleAttachment(iMsgId);
                                            }
                                            if (succeeded) {
                                                Toast t = Toast.makeText(mContext,
                                                        R.string.copy_to_sdcard_success,
                                                        Toast.LENGTH_SHORT);
                                                t.show();
                                            } else {
                                                Toast t = Toast.makeText(mContext,
                                                        R.string.copy_to_sdcard_fail,
                                                        Toast.LENGTH_SHORT);
                                                t.show();
                                            }
                                        } else if (filesize > 1) {
                                            Bundle data = new Bundle();
                                            data.putInt("savecontent",
                                                    IMmsAttachmentEnhanceExt.MMS_SAVE_OTHER_ATTACHMENT);
                                            Intent i = new Intent(mContext, MultiSaveActivity.class);
                                            i.putExtra("msgid", iMsgId);
                                            i.putExtras(data);
                                            // mContext.startActivityForResult(i,0);
                                            mContext.startActivity(i);
                                        }
                                    }
                                });
                        b.setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                public final void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                        b.create().show();
                    }
                });
            }
        }
        /// @}
        ImageView thumb = (ImageView) mFileAttachmentView.findViewById(R.id.file_attachment_thumbnail);
        TextView name = (TextView) mFileAttachmentView.findViewById(R.id.file_attachment_name_info);
        TextView name2 = (TextView) mFileAttachmentView.findViewById(R.id.file_attachment_name_info2);
        if (isCtFeature) {
            thumb.setVisibility(View.GONE);
            thumb = (ImageView) mFileAttachmentView.findViewById(R.id.file_attachment_thumbnail2);
            thumb.setVisibility(View.VISIBLE);
        }
        String nameText = "";
        int thumbResId = -1;
        if (attach.isVCard()) {
            if (isCtFeature) {
                nameText = ((VCardModel) attach).getDisplayName().toString();
                if (TextUtils.isEmpty(nameText)) {
                    nameText = mContext.getString(R.string.file_attachment_vcard_name, attach.getSrc());
                }
                thumbResId = R.drawable.ipmsg_chat_contact_vcard;
                if (name2 != null && ((VCardModel) attach).getContactCount() > 1) {
                    name2.setText(" +" + (((VCardModel) attach).getContactCount() - 1));
                    name2.setVisibility(View.VISIBLE);
                }
            } else {
                nameText = mContext.getString(R.string.file_attachment_vcard_name, attach.getSrc());
                thumbResId = R.drawable.ic_vcard_attach;
            }
        } else if (attach.isVCalendar()) {
            nameText = mContext.getString(R.string.file_attachment_vcalendar_name, attach.getSrc());
            if (isCtFeature) {
                thumbResId = R.drawable.ipmsg_chat_contact_calendar;
            } else {
                thumbResId = R.drawable.ic_vcalendar_attach;
            }
        }
        /// M: add for attachmentEnhance @{
        if (MmsConfig.isSupportAttachEnhance()) {
            if (files.size() == 1 && !attach.isVCard() && !attach.isVCalendar()) {
                nameText = attach.getSrc();
                thumbResId = R.drawable.unsupported_file;
                MmsLog.i(TAG, "filesize=1, add attach view");
            } else if (files.size() > 1) {
                nameText = mContext.getString(
                                R.string.file_attachment_common_name,
                                mContext.getString(R.string.file_attachment_contains)
                           + String.valueOf(files.size())
                           + mContext.getString(R.string.file_attachment_files));
                thumbResId = R.drawable.multi_files;
                MmsLog.i(TAG, "filesize>>1, add attach view");
            }
        }
        /// @}
        name.setText(nameText);
        /// M: add for OP09 @{
        if ((!isCtFeature || !attach.isVCard() || ((VCardModel) attach).getContactCount() <= 1)
                && name2 != null) {
            name2.setText("");
            name2.setVisibility(View.GONE);
        }
        /// @}
        thumb.setImageResource(thumbResId);
        final TextView size = (TextView) mFileAttachmentView.findViewById(R.id.file_attachment_size_info);
        if (MmsConfig.isSupportAttachEnhance()) {
            //OP01
            size.setText(MessageUtils.getHumanReadableSize(attachSize));
        } else {
            //Not OP01
            size.setText(MessageUtils.getHumanReadableSize(attach.getAttachSize()));
        }
    }
    // Add for vCard end


/// M: @{

    // add for attachment enhance
    // For save single attachment(Include: copySingleAttachment,copyPart,getUniqueDestination)
    /* This function is for save single text or html attachment into filemgr */
    private boolean copyTextSingleAttachment(long msgId) {
        boolean result = false;

        PduBody body;
         try {
                body = SlideshowModel.getPduBody(mContext, ContentUris.withAppendedId(Mms.CONTENT_URI, msgId));
         } catch (MmsException e) {
                 Log.e(TAG, e.getMessage(), e);
                 return false;
         }
        int i = 0;

        if (body == null) {
            return false;
        }

        PduPart part = null;
        String mSrc = null;
        String mContentType = null;

        SlideshowModel mSlideshow = null;

        try {
             mSlideshow = SlideshowModel.createFromPduBody(mContext, body);
        } catch (MmsException e) {
             MmsLog.v(TAG, "Create from pdubody exception!");
             return false;
        }

        ArrayList<FileAttachmentModel> attachmentList = mSlideshow.getAttachFiles();

        byte[] data = null;
        data = attachmentList.get(0).getData();

        mContentType = new String(attachmentList.get(0).getContentType());

        //get part filename
        mSrc = attachmentList.get(0).getSrc();

        //format filename
        mSrc = formatFileName(mSrc);

        if (mSrc == null) {
            MmsLog.v(TAG, "copyTextSingleAttachment() File name == null");
            return false;
        }

        if (!mContentType.equals("text/plain") && !mContentType.equals("text/html")) {
            MmsLog.v(TAG, "copyTextSingleAttachment() It is not a text or html attachment");
            return false;
        }
        result = copyTextPart(data, mSrc);

       MmsLog.i(TAG, "copyTextSingleAttachment() result is " + result);
       return result;
    }

    private boolean copyTextPart(byte[] data, String filename) {
        FileOutputStream fout = null;
        try {
            File file = MessageUtils.getStorageFile(filename, mContext.getApplicationContext());
            if (file == null) {
                return false;
            }
            fout = new FileOutputStream(file);
            fout.write(data, 0, data.length);
        } catch (IOException e) {
            MmsLog.e(TAG, "IOException caught while opening or reading stream", e);
                return false;
            } finally {
                 if (null != fout) {
                    try {
                        fout.close();
                    } catch (IOException e) {
                        // Ignore
                        MmsLog.e(TAG, "IOException caught while closing stream", e);
                        return false;
                    }
                }
            }
           return true;

    }

    private boolean copySingleAttachment(long msgId) {
        boolean result = false;
        // PduBody body = ComposeMessageActivity.getPduBody(mContext, msgId);
        PduBody body;
        try {
            body = SlideshowModel.getPduBody(mContext, ContentUris.withAppendedId(Mms.CONTENT_URI, msgId));
        } catch (MmsException e) {
            Log.e(TAG, e.getMessage(), e);
            return false;
        }

        if (body == null) {
            return false;
        }
        int partNum = body.getPartsNum();
        PduPart part = null;
        String mSrc = null;

        SlideshowModel mSlideshow = null;
        try {
            mSlideshow = SlideshowModel.createFromPduBody(mContext, body);
        } catch (MmsException e) {
             MmsLog.v(TAG, "Create from pdubody exception!");
             return false;
        }

        for (int i = 0; i < partNum; i++) {
            part = body.getPart(i);
            byte[] cl = part.getContentLocation();
            byte[] name = part.getName();
            byte[] ci = part.getContentId();
            byte[] fn = part.getFilename();
            //get part filename
            if (cl != null) {
                mSrc = new String(cl);
            } else if (name != null) {
                mSrc = new String(name);
            } else if (ci != null) {
               mSrc = new String(ci);
            } else if (fn != null) {
               mSrc = new String(fn);
            } else {
               continue;
            }

            // get part uri
            String PartUri = null;

            if (part.getDataUri() != null) {
                MmsLog.e(TAG, "part Uri = " + part.getDataUri().toString());
                PartUri = part.getDataUri().toString();
            } else {
                MmsLog.v(TAG, "PartUri = null");
                continue;
            }
            ArrayList<FileAttachmentModel> attachmentList = mSlideshow.getAttachFiles();

            for (int k = 0; k < attachmentList.size(); k++) {
                if (attachmentList.get(k).getUri() != null) {
                    if (PartUri.compareTo(attachmentList.get(k).getUri().toString()) == 0) {
                        //MmsLog.v(TAG, "part.getFilename() = "+part.getFilename());
                        result = true;
                        break;
                     }
                } else {
                    result = false;
                }
            }

            if (result) {
                break;
            }
          }

          if (result) {
              mSrc = formatFileName(mSrc);
              copyPart(part, mSrc);
          } else {
              MmsLog.i(TAG, "There is no a correct part! ");
          }

          return result;
  }

  private boolean copyPart(PduPart part, String filename) {
      Uri uri = part.getDataUri();
      MmsLog.i(TAG, "copyPart, copy part into sdcard uri " + uri);

      InputStream input = null;
      FileOutputStream fout = null;
      try {
          ContentResolver mContentResolver = mContext.getContentResolver();
          input = mContentResolver.openInputStream(uri);
          if (input instanceof FileInputStream) {
              FileInputStream fin = (FileInputStream) input;
              // Depending on the location, there may be an
              // extension already on the name or not
              String dir = "";
              File file = MessageUtils.getStorageFile(filename, mContext.getApplicationContext());
              if (file == null) {
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
              mContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                      Uri.fromFile(file)));
          }
      } catch (IOException e) {
          // Ignore
          MmsLog.e(TAG, "IOException caught while opening or reading stream", e);
          return false;
      } finally {
          if (null != input) {
              try {
                  input.close();
              } catch (IOException e) {
                  // Ignore
                  MmsLog.e(TAG, "IOException caught while closing stream", e);
                  return false;
              }
          }
          if (null != fout) {
              try {
                  fout.close();
              } catch (IOException e) {
                  // Ignore
                  MmsLog.e(TAG, "IOException caught while closing stream", e);
                  return false;
              }
          }
      }
      return true;
  }

/*
  private File getUniqueDestination(String fileName) {
      final int index = fileName.indexOf(".");
      final String extension = fileName.substring(index + 1, fileName.length());
      final String base = fileName.substring(0, index);
      File file = new File(base + "." + extension);
      for (int i = 2; file.exists(); i++) {
          file = new File(base + "_" + i + "." + extension);
      }
      return file;
  }
  */
    // Get rid of illegal characters in filename
    private String formatFileName(String fileName) {

        if (fileName == null) {
            MmsLog.i(TAG, "In formatFileName filename = null");
            return null;
        }

        String extension  = null;
        int index;
        if ((index = fileName.indexOf(".")) != -1) {
            extension = fileName.substring(index + 1, fileName.length());
            fileName = fileName.substring(0, index);
        }
        final String regex = "[:\\/?,. ]";
        fileName = fileName.replaceAll(regex, "_");
        fileName = fileName.replaceAll("<", "");
        fileName = fileName.replaceAll(">", "");

        MmsLog.i(TAG, "getNameFromPart, fileName is " + fileName + ", extension is " + extension);

        return fileName + "." + extension;
    }
  /// @}
    /// M: add for ipmessage
    public void bindIpmsg(MessageItem msgItem, boolean isDeleteMode) {
        /// M: add for ipmessage, notification listener
        if (!isRegistNotificationListener) {
            MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "listItem.bindIpmsg(): regist noti listener.");
            isRegistNotificationListener = true;
            IpMessageUtils.addIpMsgNotificationListeners(mContext, this);
        }
        Log.d(IPMSG_TAG, "bindIpmsg(): msg id = " + msgItem.mMsgId + ", context = " + mContext);
        if (null == msgItem.mIpMessage) {
            msgItem.mIpMessage = IpMessageUtils.getMessageManager(mContext).getIpMsgInfo(msgItem.mMsgId);
            if (null == msgItem.mIpMessage) {
                MmsLog.d(IPMSG_TAG, "bindIpmsg(): ip message is null!");
                bindDefault(null, false);
                return;
            }
        }
        /// M: hide file transfer view
        if (mIpmsgFileDownloadContrller != null) {
            mIpmsgFileDownloadContrller.setVisibility(View.GONE);
        }
        if (mIpmsgFileDownloadView != null) {
            mIpmsgFileDownloadView.setVisibility(View.GONE);
        }
        int ipMsgStatus = msgItem.mIpMessage.getStatus();
        boolean isFileTransferStatus = isFileTransferStatus(ipMsgStatus);
        boolean showContent = isIpMessageShowContent(ipMsgStatus);
        switch (msgItem.mIpMessage.getType()) {
        case IpMessageType.TEXT:
            setIpTextItem(msgItem, isDeleteMode);
            break;
        case IpMessageType.PICTURE:
            setIpImageItem(msgItem, isDeleteMode, isFileTransferStatus, showContent);
            break;
        case IpMessageType.VOICE:
            setIpVoiceItem(msgItem, isDeleteMode, isFileTransferStatus, showContent);
            break;
        case IpMessageType.VCARD:
            setIpVCardItem(msgItem, isDeleteMode, isFileTransferStatus, showContent);
            break;
        case IpMessageType.VIDEO:
            setIpVideoItem(msgItem, isDeleteMode, isFileTransferStatus, showContent);
            break;
        case IpMessageType.CALENDAR:
            setIpVCalendarItem(msgItem, isDeleteMode, isFileTransferStatus, showContent);
            break;
        case IpMessageType.UNKNOWN_FILE:
        case IpMessageType.COUNT:
            MmsLog.e(IPMSG_TAG, "bindIpmsg(): Unknown IP message type. type = " + msgItem.mIpMessage.getType());
            break;
        case IpMessageType.GROUP_CREATE_CFG:
        case IpMessageType.GROUP_ADD_CFG:
        case IpMessageType.GROUP_QUIT_CFG:
            /// M: group chat type
            MmsLog.e(IPMSG_TAG, "bindIpmsg(): Group IP message type. type = " + msgItem.mIpMessage.getType());
            break;
        default:
            MmsLog.e(IPMSG_TAG, "bindIpmsg(): Error IP message type. type = " + msgItem.mIpMessage.getType());
            break;
        }

        mSubStatus.setVisibility(View.VISIBLE);
        CharSequence subCharSequence;
        // Add for joyn message
        String subStatus = IpMessageUtils.getResourceManager(mContext).getSimStatus();
        if (null != subStatus) {        
              setSubIconAndLabel(subStatus);
//            subCharSequence = MessageUtils.getJoynSubInfo(mContext, msgItem.mSubId, subStatus);
          } else {
              setSubIconAndLabel(null);
//            subCharSequence = formatSubStatus(msgItem);
          }
         //mSubStatus.setText(subCharSequence);
		/*HQ_zhangjing 2015-10-08 modified for CQ HQ01432443 */
        if ((!msgItem.isSending() && TextUtils.isEmpty(msgItem.mTimestamp))) {
            mDateView.setVisibility(View.GONE);
        } else {
            mDateView.setVisibility(View.VISIBLE);
            /// M: google jb.mr1 patch, group mms
            mDateView.setText(msgItem.isSending() ?
                mContext.getResources().getString(R.string.sending_message)
                        : buildTimestampLine(msgItem.mTimestamp));
        }
        /** M: add download file view for IP message, if isFileTransferStatus is true,
         *  that means this is a rcse defined file transfer message.
         */
        if (isFileTransferStatus) {
            drawDownloadFileView(msgItem, ipMsgStatus, msgItem.mIpMessage.getType());
        }
        drawRightStatusIndicator(msgItem);
        requestLayout();
    }

    private void setIpTextItem(MessageItem msgItem, boolean isDeleteMode) {
        MmsLog.d(IPMSG_TAG, "setIpTextItem(): message Id = " + msgItem.mMsgId);
        IpTextMessage textMessage = (IpTextMessage) msgItem.mIpMessage;
        if (TextUtils.isEmpty(textMessage.getBody())) {
            MmsLog.w(IPMSG_TAG, "setIpTextItem(): No message content!");
            return;
        }
        CharSequence formattedMessage = msgItem.getCachedFormattedMessage();
        if (formattedMessage == null) {
            formattedMessage = formatMessage(msgItem, textMessage.getBody(), null,
                    msgItem.mHighlight, null);
            msgItem.setCachedFormattedMessage(formattedMessage);
        }
        mBodyTextView.setVisibility(View.VISIBLE);
        mBodyTextView.setText(formattedMessage);

        // / M: add for ip message, hide audio, vCard, vCalendar
        mIpImageView.setVisibility(View.GONE);
        mIpAudioView.setVisibility(View.GONE);
        //mCaptionSeparator.setVisibility(View.GONE);
        mCaption.setVisibility(View.GONE);
        mIpVCardView.setVisibility(View.GONE);
        mIpVCalendarView.setVisibility(View.GONE);
    }

    private void setIpImageItem(MessageItem msgItem, boolean isDeleteMode,
            boolean isFileTransferStatus, boolean showContent) {
        IpImageMessage imageMessage = (IpImageMessage) msgItem.mIpMessage;
        MmsLog.d(IPMSG_TAG, "setIpImageItem(): message Id = " + msgItem.mMsgId
                + " ipThumbPath:" + imageMessage.getThumbPath() + " imagePath:"
                + imageMessage.getPath());
        mIpImageView.setVisibility(View.VISIBLE);

        // joyn add for show thumbnail when recieved image message
        if (!TextUtils.isEmpty(imageMessage.getThumbPath())) {
            showContent = true;
        }
        if (isFileTransferStatus && !showContent) {
            mIpImageView.setVisibility(View.GONE);
        } else if (!setPicView(msgItem, imageMessage.getPath())) {
            setPicView(msgItem, imageMessage.getThumbPath());
        }

        mIpImageSizeBg.setVisibility(View.GONE);

        if (!TextUtils.isEmpty(imageMessage.getCaption())) {
            //mCaptionSeparator.setVisibility(View.VISIBLE);
            mCaption.setVisibility(View.VISIBLE);
            mCaption.setText(imageMessage.getCaption());
        } else {
            //mCaptionSeparator.setVisibility(View.GONE);
            mCaption.setVisibility(View.GONE);
        }

        // / M: add for ip message, hide text, audio, vCard, vCalendar
        mBodyTextView.setVisibility(View.GONE);
        mIpAudioView.setVisibility(View.GONE);
        mIpVCardView.setVisibility(View.GONE);
        mIpVCalendarView.setVisibility(View.GONE);
        mMediaPlayView.setVisibility(View.GONE);
    }

    private void setIpVoiceItem(MessageItem msgItem,
                                boolean isDeleteMode,
                                boolean isFileTransferStatus,
                                boolean showContent) {
        MmsLog.d(IPMSG_TAG, "setIpVoiceItem(): message Id = " + msgItem.mMsgId);
        IpVoiceMessage voiceMessage = (IpVoiceMessage) msgItem.mIpMessage;
//        mAudioOrVcardIcon.setImageResource(R.drawable.ic_soundrecorder);
        /// M: add for ip message, show audio view
        mIpAudioView.setVisibility(View.VISIBLE);

        if (TextUtils.isEmpty(voiceMessage.getCaption())) {
            //mCaptionSeparator.setVisibility(View.GONE);
            mCaption.setVisibility(View.GONE);
        } else {
            //mCaptionSeparator.setVisibility(View.VISIBLE);
            mCaption.setVisibility(View.VISIBLE);
            mCaption.setText(voiceMessage.getCaption());
        }

        // / M: add for ip message, hide text, image, audio, vCard, vCalendar
        mBodyTextView.setVisibility(View.GONE);
        mIpImageView.setVisibility(View.GONE);
        mIpVCardView.setVisibility(View.GONE);
        mIpVCalendarView.setVisibility(View.GONE);
    }

    private void setIpVCardItem(MessageItem msgItem,
                                boolean isDeleteMode,
                                boolean isFileTransferStatus,
                                boolean showContent) {
        MmsLog.d(IPMSG_TAG, "setIpVCardItem(): message Id = " + msgItem.mMsgId);
        IpVCardMessage vCardMessage = (IpVCardMessage) msgItem.mIpMessage;
        String name = vCardMessage.getName();
        if (name != null && name.lastIndexOf(".") != -1) {
            name = name.substring(0, name.lastIndexOf("."));
        }
        if (isFileTransferStatus && !showContent) {
            mIpVCardView.setVisibility(View.GONE);
        } else {
            mVCardInfo.setText(name);
            mIpVCardView.setVisibility(View.VISIBLE);
        }

        // / M: add for ip message, hide text, image, audio, vCalendar
        mBodyTextView.setVisibility(View.GONE);
        mIpImageView.setVisibility(View.GONE);
        mIpAudioView.setVisibility(View.GONE);
        //mCaptionSeparator.setVisibility(View.GONE);
        mCaption.setVisibility(View.GONE);
        mIpVCalendarView.setVisibility(View.GONE);
    }

    private void setIpVCalendarItem(MessageItem msgItem,
                                    boolean isDeleteMode,
                                    boolean isFileTransferStatus,
                                    boolean showContent) {
        MmsLog.d(IPMSG_TAG, "setIpVCalendarItem(): message Id = " + msgItem.mMsgId);
        IpVCalendarMessage vCalendarMessage = (IpVCalendarMessage) msgItem.mIpMessage;
        String summary = vCalendarMessage.getSummary();
        if (summary != null && summary.lastIndexOf(".") != -1) {
            summary = summary.substring(0, summary.lastIndexOf("."));
        }
        if (isFileTransferStatus && !showContent) {
            mIpVCalendarView.setVisibility(View.GONE);
        } else {
            mVCalendarInfo.setText(summary);
            mIpVCalendarView.setVisibility(View.VISIBLE);
        }

        mBodyTextView.setVisibility(View.GONE);
        mIpImageView.setVisibility(View.GONE);
        mIpAudioView.setVisibility(View.GONE);
        //mCaptionSeparator.setVisibility(View.GONE);
        mCaption.setVisibility(View.GONE);
        mIpVCardView.setVisibility(View.GONE);
    }

    private void setIpVideoItem(MessageItem msgItem,
                                boolean isDeleteMode,
                                boolean isFileTransferStatus,
                                boolean showContent) {
        MmsLog.d(IPMSG_TAG, "setIpVideoItem(): message Id = " + msgItem.mMsgId);
        mIpImageView.setVisibility(View.VISIBLE);
        mMediaPlayView.setVisibility(View.VISIBLE);
        IpVideoMessage videoMessage = (IpVideoMessage) msgItem.mIpMessage;

        if (isFileTransferStatus && !showContent) {
            mIpImageView.setVisibility(View.GONE);
            mMediaPlayView.setVisibility(View.GONE);
        } else {
            setVideoView(videoMessage.getPath(), videoMessage.getThumbPath());
        }
        mIpImageSizeBg.setVisibility(View.GONE);

        if (TextUtils.isEmpty(videoMessage.getCaption())) {
            //mCaptionSeparator.setVisibility(View.GONE);
            mCaption.setVisibility(View.GONE);
        } else {
            //mCaptionSeparator.setVisibility(View.VISIBLE);
            mCaption.setVisibility(View.VISIBLE);
            mCaption.setText(videoMessage.getCaption());
        }

        // / M: add for ip message, hide text, audio, vCard, vCalendar
        mBodyTextView.setVisibility(View.GONE);
        mIpAudioView.setVisibility(View.GONE);
        mIpVCardView.setVisibility(View.GONE);
        mIpVCalendarView.setVisibility(View.GONE);
    }

    private boolean setPicView(MessageItem msgItem, String filePath) {
        MmsLog.d(IPMSG_TAG, "setPicView(): filePath = " + filePath + ", imageView = " + mImageContent);
        if (TextUtils.isEmpty(filePath) || null == mImageContent) {
            return false;
        }
        Bitmap bitmap = msgItem.getIpMessageBitmap();
        if (null == bitmap) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            bitmap = BitmapFactory.decodeFile(filePath, options);
            int width = options.outWidth;
            int height = options.outHeight;
            int w = options.outWidth;

            /// M: get screen width
            DisplayMetrics dm = new DisplayMetrics();
            int screenWidth = 0;
            WindowManager wmg = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
            wmg.getDefaultDisplay().getMetrics(dm);
            if (dm.heightPixels > dm.widthPixels) {
                screenWidth = dm.widthPixels;
            } else {
                screenWidth = dm.heightPixels;
            }
            /// M: the returned bitmap's w/h is different with the input!
            if (width > screenWidth * MAX_SCALE) {
                w = (int) (screenWidth * MAX_SCALE);
                bitmap = IpMessageUtils.getBitmapByPath(filePath, options, w, height * w / width);
                msgItem.setIpMessageBitmapSize(w, height * w / width);
            } else if (width > screenWidth * MIN_SCALE) {
                w = (int) (screenWidth * MIN_SCALE);
                bitmap = IpMessageUtils.getBitmapByPath(filePath, options, w, height * w / width);
                msgItem.setIpMessageBitmapSize(w, height * w / width);
            } else {
                bitmap = IpMessageUtils.getBitmapByPath(filePath, options, width, height);
                msgItem.setIpMessageBitmapSize(width, height);
            }

            msgItem.setIpMessageBitmapCache(bitmap);
        }

        if (null != bitmap) {
            ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) mImageContent.getLayoutParams();
            params.height = msgItem.getIpMessageBitmapHeight();
            params.width = msgItem.getIpMessageBitmapWidth();
            mImageContent.setLayoutParams(params);
            mImageContent.setImageBitmap(bitmap);
            return true;
        } else {
            mImageContent.setImageResource(R.drawable.ic_missing_thumbnail_picture);
            return false;
        }
    }

    public void setVideoView(String path, String bakPath) {
        Bitmap bp = null;
        int degree = 0;
        mMediaPlayView.setVisibility(View.VISIBLE);

        if (!TextUtils.isEmpty(path)) {
            bp = ThumbnailUtils.createVideoThumbnail(path, Thumbnails.MICRO_KIND);
            degree = IpMessageUtils.getExifOrientation(path);
        }

        if (null == bp) {
            if (!TextUtils.isEmpty(bakPath)) {
                BitmapFactory.Options options = IpMessageUtils.getOptions(bakPath);
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(bakPath, options);
                bp = IpMessageUtils.getBitmapByPath(bakPath, IpMessageUtils.getOptions(bakPath),
                        options.outWidth, options.outHeight);
                degree = IpMessageUtils.getExifOrientation(bakPath);
            }
        }
        /** M: we use the same view show image/big video snap, but they should have different property.
         *  image layout change to a dynamic size, big video snap is still wrap_content
         *  we change ipmessage image layout to keep uniform with group chat activity.
         */
        ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) mImageContent.getLayoutParams();
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        mImageContent.setLayoutParams(params);

        if (null != bp) {
            if (degree != 0) {
                bp = IpMessageUtils.rotate(bp, degree);
            }
            mImageContent.setImageBitmap(bp);
        } else {
            mImageContent.setImageResource(R.drawable.ic_missing_thumbnail_picture);
        }
    }

    /// M: add for ip message, dividers
    private void bindDividers(MessageItem msgItem, boolean isDeleteMode) {
        MmsLog.d(TAG_DIVIDER, "listItem.bindDividers(): draw time divider ?= " + msgItem.mIsDrawTimeDivider);
        MmsLog.d(TAG_DIVIDER, "listItem.bindDividers(): draw unread divider ?= " + msgItem.mIsDrawUnreadDivider
                + ", unread count = " + msgItem.mUnreadCount);
        MmsLog.d(TAG_DIVIDER, "listItem.bindDividers(): draw online divider ?= " + msgItem.mIsDrawUnreadDivider
            + ", unread count = " + msgItem.mUnreadCount);
        if (null != mTimeDivider) {
            if (msgItem.mIsDrawTimeDivider) {
			/*HQ_zhangjing add for al812 mms ui */
                //mTimeDivider.setVisibility(View.VISIBLE);
                mTimeDividerStr.setText(msgItem.mTimeDividerString);
            } else {
                mTimeDivider.setVisibility(View.GONE);
            }
        }
        if (!isDeleteMode && null != mUnreadDivider) {
            if (msgItem.mIsDrawUnreadDivider && msgItem.mUnreadCount > 0) {
			/*HQ_zhangjing add for al812 mms ui */
                //mUnreadDivider.setVisibility(View.VISIBLE);
                mUnreadDividerStr.setText(
                    String.format(mContext.getResources().getString(R.string.str_ipmsg_unread_format),
                        msgItem.mUnreadCount));
            } else {
                mUnreadDivider.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void notificationsReceived(Intent intent) {
        MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "listItem.notificationsReceived(): intent = " + intent);
        String action = intent.getAction();
        if (TextUtils.isEmpty(action)) {
            return;
        }
        long msgId = 0L;
        switch (IpMessageUtils.getActionTypeByAction(action)) {
        case IpMessageUtils.IPMSG_DOWNLOAD_ATTACH_STATUS_ACTION:
            MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "listItem.notificationsReceived():" +
                " download status notification.");
            if (null != mMessageItem) {
                try {
                    int downloadStatus = intent.getIntExtra(DownloadAttachStatus.DOWNLOAD_MSG_STATUS,
                        DownloadAttachStatus.STARTING);
                    MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "notificationsReceived(): downloadStatus = "
                        + downloadStatus);
                    if (downloadStatus == DownloadAttachStatus.DONE) {
                        MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG,
                            "notificationsReceived(): call UI thread notify data set change.");
                        final Message msg = Message.obtain(mHandler, MessageListAdapter.MSG_LIST_NEED_REFRASH,
                            MessageListAdapter.MESSAGE_LIST_REFRASH_WITH_CLEAR_CACHE, 0);
                        msg.sendToTarget();
                        return;
                    }
                    msgId = intent.getLongExtra(DownloadAttachStatus.DOWNLOAD_MSG_ID, 0);
                    updateMessageItemState(msgId);
                } catch (NullPointerException e) {
                    MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "NullPointerException:" + e.toString());
                }
            }
            break;
        case IpMessageUtils.IPMSG_IP_MESSAGE_STATUS_ACTION:
            MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG,
                        "listItem.notificationsReceived(): download status notification.");
            if (null != mMessageItem) {
                try {
                    msgId = intent.getLongExtra(IpMessageStatus.IP_MESSAGE_ID, 0);
                    updateMessageItemState(msgId);
                } catch (NullPointerException e) {
                    MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "NullPointerException:" + e.toString());
                }
            }
            break;
        default:
            MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG,
                    "listItem.notificationsReceived(): ignore notification.");
            return;
        }
    }

    private void updateMessageItemState(long msgId) {
        if (mMessageItem.mIpMessageId <= 0 || mMessageItem.mMsgId != msgId) {
            return;
        }
        final long messageId = msgId;
        ((Activity) mContext).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean deleteMode = mMessageListAdapter == null ? false : mMessageListAdapter.mIsDeleteMode;
                if (deleteMode) {
                    long id = mMessageListAdapter.getKey("sms", messageId);
                    if (mMessageListAdapter.mListItem.get(id) == null) {
                        mMessageListAdapter.mListItem.put(id, false);
                    } else {
                        mMessageItem.setSelectedState(mMessageListAdapter.mListItem.get(id));
                    }
                }
                bind(mMessageItem, false, 0, deleteMode);
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        /// M: add for ip message, remove notification listener
        if (isRegistNotificationListener) {
            MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "listItem.unbind(): remove noti listener.");
            IpMessageUtils.removeIpMsgNotificationListeners(mContext, this);
            isRegistNotificationListener = false;
        }
    }

    private void openMedia(IpMessage ipmessage, long msgId) {
        MmsLog.d(IPMSG_TAG, "openMedia(): msgId = " + msgId);
        if (ipmessage.getType() == IpMessageType.VCARD) {
            IpVCardMessage msg = (IpVCardMessage) ipmessage;
            if (TextUtils.isEmpty(msg.getPath())) {
                MmsLog.e(IPMSG_TAG, "openMedia(): open vCard failed.");
                return;
            }
            if (!IpMessageUtils.getSDCardStatus()) {
                IpMessageUtils.createLoseSDCardNotice(mContext,
                        IpMessageUtils.getResourceManager(mContext)
                                    .getSingleString(IpMessageConsts.string.ipmsg_cant_share));
                return;
            }
            if (MessageUtils.getAvailableBytesInFileSystemAtGivenRoot(StorageManagerEx.getDefaultPath())
                    < msg.getSize()) {
                Toast.makeText(mContext, mContext.getString(R.string.export_disk_problem), Toast.LENGTH_LONG).show();
            }
            String dest = IpMessageUtils.getCachePath(mContext) + "temp"
                + msg.getPath().substring(msg.getPath().lastIndexOf(".vcf"));
            IpMessageUtils.copy(msg.getPath(), dest);
            File vcardFile = new File(dest);
            Uri vcardUri = Uri.fromFile(vcardFile);
            Intent i = new Intent();
            i.setAction(android.content.Intent.ACTION_VIEW);
            i.setDataAndType(vcardUri, "text/x-vcard");
            mContext.startActivity(i);
        } else if (ipmessage.getType() == IpMessageType.CALENDAR) {
            IpVCalendarMessage msg = (IpVCalendarMessage) ipmessage;
            if (TextUtils.isEmpty(msg.getPath())) {
                MmsLog.e(IPMSG_TAG, "openMedia(): open vCalendar failed.");
                return;
            }
            if (!IpMessageUtils.getSDCardStatus()) {
                IpMessageUtils.createLoseSDCardNotice(mContext,
                        IpMessageUtils.getResourceManager(mContext)
                                    .getSingleString(IpMessageConsts.string.ipmsg_cant_share));
                return;
            }
            if (MessageUtils.getAvailableBytesInFileSystemAtGivenRoot(StorageManagerEx.getDefaultPath())
                    < msg.getSize()) {
                Toast.makeText(mContext, mContext.getString(R.string.export_disk_problem), Toast.LENGTH_LONG).show();
            }
            String dest = IpMessageUtils.getCachePath(mContext) + "temp"
                + msg.getPath().substring(msg.getPath().lastIndexOf(".vcs"));
            IpMessageUtils.copy(msg.getPath(), dest);
            File calendarFile = new File(dest);
            Uri calendarUri = Uri.fromFile(calendarFile);
            Intent intent = new Intent();
            intent.setAction(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(calendarUri, "text/x-vcalendar");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                mContext.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                MmsLog.e(IPMSG_TAG, "can't open calendar");
            }
        } else {
            Intent intent = new Intent(RemoteActivities.MEDIA_DETAIL);
            intent.putExtra(RemoteActivities.KEY_MESSAGE_ID, (int) msgId);
            IpMessageUtils.startRemoteActivity(mContext, intent);
        }
    }

    private boolean isFileTransferStatus(int ipMsgStatus) {
        switch (ipMsgStatus) {
        case IpMessageStatus.MO_INVITE:
        case IpMessageStatus.MO_SENDING:
        case IpMessageStatus.MO_REJECTED:
        case IpMessageStatus.MO_SENT:
        case IpMessageStatus.MO_CANCEL:
        case IpMessageStatus.MT_INVITED:
        case IpMessageStatus.MT_REJECT:
        case IpMessageStatus.MT_RECEIVING:
        case IpMessageStatus.MT_RECEIVED:
        case IpMessageStatus.MT_CANCEL:
        case IpMessageStatus.MO_PAUSE:
        case IpMessageStatus.MO_RESUME:
        case IpMessageStatus.MT_PAUSE:
        case IpMessageStatus.MT_RESUME:
            return true;
        default:
            return false;
        }
    }

    private boolean isIpMessageShowContent(int ipMsgStatus) {
        switch (ipMsgStatus) {
        case IpMessageStatus.MO_INVITE:
        case IpMessageStatus.MO_SENDING:
        case IpMessageStatus.MO_REJECTED:
        case IpMessageStatus.MO_SENT:
        case IpMessageStatus.MT_RECEIVED:
        case IpMessageStatus.MO_PAUSE:
        case IpMessageStatus.MO_RESUME:
            return true;
        case IpMessageStatus.MO_CANCEL:
        case IpMessageStatus.MT_INVITED:
        case IpMessageStatus.MT_REJECT:
        case IpMessageStatus.MT_RECEIVING:
        case IpMessageStatus.MT_CANCEL:
        case IpMessageStatus.MT_PAUSE:
        case IpMessageStatus.MT_RESUME:
            return false;
        default:
            return true;
        }
    }

    private void drawDownloadFileView(final MessageItem msgItem, final int ipMsgStatus, int ipMsgType) {
        MmsLog.d(IPMSG_TAG, "drawDownloadFileView(): msgId = " + msgItem.mMsgId + ", ipMsgStatus = " + ipMsgStatus
            + ", ipMsgType = " + ipMsgType);
        if (mIpmsgFileDownloadContrller == null || mIpmsgFileDownloadView == null) {
            MmsLog.d(IPMSG_TAG, "drawDownloadFileView(): mIpmsgFileDownloadContrller is NULL!");
            return;
        }
        /// M: show IP message status string
        String statusText = IpMessageUtils.getMessageManager(mContext).getIpMessageStatusString(msgItem.mMsgId);
        if (!TextUtils.isEmpty(statusText)) {
            mCaption.setVisibility(View.VISIBLE);
            mCaption.setText(statusText);
        } else {
            mCaption.setVisibility(View.GONE);
        }

        IpAttachMessage ipAttachMessage = (IpAttachMessage) msgItem.mIpMessage;
        showIpMessageThumb(ipMsgType, ipMsgStatus, ipAttachMessage);
        switch (ipMsgStatus) {
        case IpMessageStatus.MT_RECEIVING:
            //mCaptionSeparator.setVisibility(View.GONE);
        case IpMessageStatus.MO_INVITE:
        case IpMessageStatus.MO_SENDING:
        case IpMessageStatus.MO_RESUME:
        case IpMessageStatus.MO_PAUSE:
        case IpMessageStatus.MT_RESUME:
        case IpMessageStatus.MT_PAUSE:
            int progress = IpMessageUtils.getMessageManager(mContext).getDownloadProcess(msgItem.mMsgId);
            mIpmsgDownloadFileProgress.setProgress(progress);
            mIpmsgFileSize.setText((progress * ipAttachMessage.getSize() / 100) + "/" + ipAttachMessage.getSize() + "K");
            mIpmsgFileDownloadContrller.setVisibility(View.GONE);
            mIpmsgFileDownloadView.setVisibility(View.VISIBLE);
            if (mIpmsgCancelDownloadButton != null) {
                Drawable imageCancel = IpMessageUtils.getResourceManager(mContext).getSingleDrawable(
                        IpMessageConsts.drawable.ipmsg_file_transfer_cancel);
                mIpmsgCancelDownloadButton.setImageDrawable(imageCancel);
                mIpmsgCancelDownloadButton.setOnClickListener(
                    new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            MmsLog.d(IPMSG_TAG, "drawDownloadFileView(): cancel button OnClick.");
                            IpMessageUtils.getMessageManager(mContext)
                                .setIpMessageStatus(msgItem.mMsgId, IpMessageStatus.MO_CANCEL);
                        }
                    }
                );
            }

            if (mIpmsgPauseResumeButton != null) {
                    MmsLog.d(IPMSG_TAG, "mIpmsgPauseResumeButton = " + mIpmsgPauseResumeButton
                            + ", ipMsgStatus = " + ipMsgStatus);
                if (ipMsgStatus == IpMessageStatus.MO_INVITE) {
                    mIpmsgPauseResumeButton.setVisibility(View.GONE);
                } else {
                    mIpmsgPauseResumeButton.setVisibility(View.VISIBLE);
                        if (ipMsgStatus != IpMessageStatus.MO_PAUSE && ipMsgStatus != IpMessageStatus.MT_PAUSE) {
                        Drawable imagePause = IpMessageUtils.getResourceManager(mContext).getSingleDrawable(
                                IpMessageConsts.drawable.ipmsg_file_transfer_pause);
                        MmsLog.d(IPMSG_TAG, "imagePause = " + imagePause);
                        mIpmsgPauseResumeButton.setBackgroundDrawable(imagePause);
                        } else {
                        Drawable imageResume = IpMessageUtils.getResourceManager(mContext).getSingleDrawable(
                                IpMessageConsts.drawable.ipmsg_file_transfer_resume);
                        MmsLog.d(IPMSG_TAG, "imageResume = " + imageResume);
                        mIpmsgPauseResumeButton.setBackgroundDrawable(imageResume);
                        }
                }

                mIpmsgPauseResumeButton.setOnClickListener(
                    new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            MmsLog.d(IPMSG_TAG, "drawDownloadFileView(): pause button OnClick.");
                            if (ipMsgStatus == IpMessageStatus.MO_PAUSE || ipMsgStatus == IpMessageStatus.MT_PAUSE) {
                                IpMessageUtils.getMessageManager(mContext).setIpMessageStatus(msgItem.mMsgId, IpMessageStatus.MO_RESUME);
                            } else {
                                IpMessageUtils.getMessageManager(mContext).setIpMessageStatus(msgItem.mMsgId, IpMessageStatus.MO_PAUSE);
                            }
                        }
                    }
                );
            }

            break;
        case IpMessageStatus.MO_REJECTED:
            mIpmsgFileDownloadContrller.setVisibility(View.VISIBLE);
            mIpmsgFileDownloadView.setVisibility(View.GONE);
            if (mIpmsgResendButton != null) {
                MmsLog.d(IPMSG_TAG, "drawDownloadFileView(): Set resend button OnClickListener.");
                mIpmsgResendButton.setOnClickListener(
                    new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            MmsLog.d(IPMSG_TAG, "drawDownloadFileView(): Resend button OnClick.");
                            IpMessageUtils.getMessageManager(mContext)
                                .setIpMessageStatus(msgItem.mMsgId, IpMessageStatus.MO_INVITE);
                        }
                    }
                );
            }
            //mCaptionSeparator.setVisibility(View.VISIBLE);
            break;
        case IpMessageStatus.MT_INVITED:
            mIpmsgFileDownloadContrller.setVisibility(View.VISIBLE);
            mIpmsgFileDownloadView.setVisibility(View.GONE);
            //mCaptionSeparator.setVisibility(View.GONE);
            MmsLog.d(IPMSG_TAG, "drawDownloadFileView(): Set accept and reject button OnClickListener.");
            if (mIpmsgAcceptButton != null) {
                mIpmsgAcceptButton.setOnClickListener(
                    new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            MmsLog.d(IPMSG_TAG, "drawDownloadFileView(): Accept button OnClick.");
                            IpMessageUtils.getMessageManager(mContext)
                                .setIpMessageStatus(msgItem.mMsgId, IpMessageStatus.MT_RECEIVING);
                        }
                    }
                );
            }
            if (mIpmsgRejectButton != null) {
                mIpmsgRejectButton.setOnClickListener(
                    new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            MmsLog.d(IPMSG_TAG, "drawDownloadFileView(): Reject button OnClick.");
                            IpMessageUtils.getMessageManager(mContext)
                                .setIpMessageStatus(msgItem.mMsgId, IpMessageStatus.MT_REJECT);
                        }
                    }
                );
            }
            break;
        case IpMessageStatus.MT_REJECT:
        case IpMessageStatus.MT_CANCEL:
            //mCaptionSeparator.setVisibility(View.GONE);
        case IpMessageStatus.MO_CANCEL:
            mIpmsgFileDownloadContrller.setVisibility(View.GONE);
            mIpmsgFileDownloadView.setVisibility(View.GONE);

            mBodyTextView.setVisibility(View.GONE);
            mIpImageView.setVisibility(View.GONE);
            /// M: add for ip message, hide audio, vCard, vCalendar
            mIpAudioView.setVisibility(View.GONE);
            //mCaptionSeparator.setVisibility(View.GONE);
            mIpVCardView.setVisibility(View.GONE);
            mIpVCalendarView.setVisibility(View.GONE);
            break;
        case IpMessageStatus.MO_SENT:
        case IpMessageStatus.MT_RECEIVED:
            mIpmsgFileDownloadContrller.setVisibility(View.GONE);
            mIpmsgFileDownloadView.setVisibility(View.GONE);
            break;
        default:
            break;
        }
    }

    private void showIpMessageThumb(int ipMsgType, int ipMsgStatus, IpAttachMessage ipAttachMessage) {
        Drawable thumb = null;
        if (ipMsgStatus >= IpMessageStatus.MO_INVITE && ipMsgStatus <= IpMessageStatus.MO_CANCEL
                && ipMsgType != IpMessageType.UNKNOWN_FILE) {
            return;
        }
        if (ipMsgType == IpMessageType.PICTURE) {
            String path = ((IpImageMessage) ipAttachMessage).getThumbPath();
            if (path != null) {
                thumb = Drawable.createFromPath(path);
            }
        } else {
            thumb = ipAttachMessage.getFileTypeIcon();
        }
        if (thumb != null) {
            mIpAudioView.setVisibility(View.GONE);
            mIpVCalendarView.setVisibility(View.GONE);
            mIpVCardView.setVisibility(View.GONE);
            mIpImageView.setVisibility(View.VISIBLE);
            mImageContent.setVisibility(View.VISIBLE);
            mIpImageSizeBg.setVisibility(View.GONE);
            mImageContent.setImageDrawable(thumb);
        }
    }

    public void setMessageListItemAdapter(MessageListAdapter adapter) {
        mMessageListAdapter = adapter;
    }

    /**
     * Init Plugin
     *
     * @param context
     */
    private void initPlugin(Context context) {
        mMmsMessageListItemPlugin = (IMmsMessageListItemExt) MPlugin
                    .createInstance(IMmsMessageListItemExt.class.getName(), context);
        MmsLog.d(TAG, "operator mMmsMessageListItemPlugin = " + mMmsMessageListItemPlugin);
        if (mMmsMessageListItemPlugin == null) {
            mMmsMessageListItemPlugin = new DefaultMmsMessageListItemExt(context);
            MmsLog.d(TAG, "default mMmsMessageListItemPlugin = " + mMmsMessageListItemPlugin);
        }
    }
    /// M: OP09 Feature: CancelDownloadMms; download mms listener;
    private OnClickListener mDownloadBtnListener = new OnClickListener() {

        @Override
        public void onClick(View arg0) {
            // add for gemini
            int subId = 0;
            // get sub id by uri
            Cursor cursor = SqliteWrapper.query(mMessageItem.mContext,
                    mMessageItem.mContext.getContentResolver(),
                    mMessageItem.mMessageUri, new String[] {
                        Telephony.Mms.SUBSCRIPTION_ID
                    }, null, null, null);
            if (cursor != null) {
                try {
                    if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                        subId = cursor.getInt(0);
                    }
                } finally {
                    cursor.close();
                }
            }
            MmsLog.d(MmsApp.TXN_TAG, "downloadMms set id : " + subId);
            Intent intent = new Intent(mContext, TransactionService.class);
            intent.putExtra(TransactionBundle.URI, mMessageItem.mMessageUri.toString());
            intent.putExtra(TransactionBundle.TRANSACTION_TYPE, Transaction.RETRIEVE_TRANSACTION);
            intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);
            mContext.startService(intent);
        }
    };

    /// M: OP09 Feature: CancelDownloadMms; listener for cancelling download mms;
    private OnClickListener mCancelDownloadListener = new OnClickListener() {
        @Override
        public void onClick(View arg0) {
            IMmsCancelDownloadExt mmsCancelDownloadExt = (IMmsCancelDownloadExt) MmsPluginManager
                    .getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_CANCEL_DOWNLOAD);
            mmsCancelDownloadExt.cancelDownload(mMessageItem.mMessageUri);
        }
    };

    /**
     * M: BUGFIX: ALPS00515336; Hide all view
     */
    public void hideAllView() {
        MmsLog.d(M_TAG, "hideAllView()");
        mSelectedBox.setVisibility(View.GONE);
        setLongClickable(false);
        setFocusable(false);
        setClickable(false);

        if (mMmsView != null) {
            mMmsView.setVisibility(View.GONE);
        }
        if (mFileAttachmentView != null) {
            mFileAttachmentView.setVisibility(View.GONE);
        }
        /// when we fail to reload item and put in cache during 500 ms
        mBodyTextView.setVisibility(View.GONE);

        if (null != mTimeDivider) {
            mTimeDivider.setVisibility(View.GONE);
        }

        if (null != mUnreadDivider) {
            mUnreadDivider.setVisibility(View.GONE);
        }

        mDateView.setVisibility(View.GONE);
        mSubStatus.setVisibility(View.GONE);
        if (mDownloadButton != null) {
            mDownloadingLabel.setVisibility(View.GONE);
            mDownloadButton.setVisibility(View.GONE);
            mMmsMessageListItemPlugin.hideAllButton();
        }
        mLockedIndicator.setVisibility(View.GONE);
        mSubStatus.setVisibility(View.GONE);
        mDeliveredIndicator.setVisibility(View.GONE);
        mDetailsIndicator.setVisibility(View.GONE);
        /// check NPE
        if (mAvatar != null && mAvatar.getVisibility() == View.VISIBLE) {
            mAvatar.setVisibility(View.GONE);
        }

        /// MTK MR1 new feature: Group Mms
        /// set Gone in bindDefault()
        if (mMessageItem != null && !mMessageItem.isMe()) {
            mSenderName = (TextView) findViewById(R.id.sender_name);
            if (mSenderName != null) {
                mSenderName.setVisibility(View.GONE);
            }
            mSendNameSeparator = findViewById(R.id.sender_name_separator);
            if (mSendNameSeparator != null) {
                mSendNameSeparator.setVisibility(View.GONE);
            }
            mSenderPhoto = (QuickContactBadge) findViewById(R.id.sender_photo);
            if (mSenderPhoto != null) {
                mSenderPhoto.setVisibility(View.GONE);
            }
        }
        requestLayout();
    }

    /**
     * M: Use default icon to display
     */
    private Drawable parseAppIcon(Context context, String url) {
        final String telPrefix = "tel:";
        final String smsPrefix = "smsto:";
        final String mailPrefix = "mailto";
        int drawableId;

        if (url.startsWith(telPrefix)) {
            drawableId = R.drawable.common_phone;
        } else if (url.startsWith(smsPrefix)) {
            drawableId = R.drawable.common_message;
        } else if (url.startsWith(mailPrefix)) {
            drawableId = R.drawable.common_email;
        } else {
            drawableId = R.drawable.common_browser;
        }
        return context.getResources().getDrawable(drawableId);
    }

    private void setSubIconAndLabel(String subName) {
        if (null != mMessageItem) {
            Log.i(TAG, "setSubIconAndLabel subId=" + mMessageItem.mSubId);
            SubscriptionInfo subInfo = SubscriptionManager.from(MmsApp.getApplication())
                    .getActiveSubscriptionInfo(mMessageItem.mSubId);
            Log.i(TAG, "subInfo=" + subInfo);
            if (null != subInfo) {
                if ((subInfo.getSimSlotIndex() == SubscriptionManager.SIM_NOT_INSERTED)
                        || (subInfo.getSimSlotIndex() == SubscriptionManager.INVALID_SUBSCRIPTION_ID)) {
                    Log.i(TAG, "current not insert sim card");
					/*HQ_zhangjing add for al812 mms ui begin*/
                    mSubStatus.setVisibility(View.GONE);
					mSubStatus.setText( mContext.getResources().getString(R.string.sim_no_service ));
					/*HQ_zhangjing add for al812 mms ui end */
                } else {
                    mSubStatus.setVisibility(View.GONE);
					/*HQ_zhangjing add for al812 mms ui */
                    //mSubStatus.setTextColor(subInfo.getIconTint());
                    if (null != subName) {
                        mSubStatus.setText(subName);
                    } else {
                        mSubStatus.setText(subInfo.getDisplayName().toString());
                    }
                }
                mHashSub.put(mMessageItem.mSubId, subInfo.getDisplayName().toString());
            } else {
                Log.i(TAG, "subInfo is null ");
				/*HQ_zhangjing add for al812 mms ui begin*/
				mSubStatus.setVisibility(View.GONE);
				mSubStatus.setText( mContext.getResources().getString(R.string.sim_no_service ));
				/*HQ_zhangjing add for al812 mms ui end*/
            }
        }
    }

	/*HQ_zhangjing 2015-10-23 modified for CQ HQ01456345 begin*/
	private void setSendStatusText( int resId ){
		if( mSendDeliveryStatus == null || mDeliveredIndicator == null || mContext == null ){
			return;
		}
		if( resId == R.drawable.im_meg_status_reach){
			mSendDeliveryStatus.setText(mContext.getResources().getString(R.string.message_has_been_received ));
			mSendDeliveryStatus.setVisibility(View.VISIBLE);
			mDeliveredIndicator.setVisibility(View.GONE);
		}else if( resId == R.drawable.im_meg_status_out ){
			mSendDeliveryStatus.setText(mContext.getResources().getString(R.string.message_has_sent_success ));
			mSendDeliveryStatus.setVisibility(View.VISIBLE);
			mDeliveredIndicator.setVisibility(View.GONE);
		}else{
			mSendDeliveryStatus.setVisibility(View.GONE);
		}
		if( resId > 0 ){
			mDeliveredIndicator.setImageResource(resId);
		}
		
	}
	/*HQ_zhangjing 2015-10-23 modified for CQ HQ01456345 end*/
	
}
