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

import com.android.mms.R;
import com.android.mms.data.WorkingMessage;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
/// M:
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.mms.ExceedMessageSizeException;
import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.model.FileAttachmentModel;
import com.android.mms.model.VCardModel;
//import com.mediatek.encapsulation.com.android.internal.telephony.EncapsulatedTelephonyService;
import com.android.mms.util.FeatureOption;
//import com.mediatek.encapsulation.android.telephony.EncapsulatedSimInfoManager;
import com.android.mms.util.MmsLog;
import com.mediatek.drm.OmaDrmClient;

import com.android.mms.util.MessageResource;
import com.mediatek.mms.ext.IMmsComposeExt;
import com.mediatek.mms.ext.DefaultMmsComposeExt;
import com.mediatek.mms.ext.ViewOnClickListener;

//add for attachment enhance by feng
//import packages
import com.mediatek.drm.OmaDrmUiUtils;
import java.util.List;
import com.mediatek.common.MPlugin;
/**
 * This is an embedded editor/view to add photos and sound/video clips
 * into a multimedia message.
 */
public class AttachmentEditor extends LinearLayout {
    private static final String TAG = "AttachmentEditor";

    static final int MSG_EDIT_SLIDESHOW   = 1;
    static final int MSG_SEND_SLIDESHOW   = 2;
    static final int MSG_PLAY_SLIDESHOW   = 3;
    static final int MSG_REPLACE_IMAGE    = 4;
    static final int MSG_REPLACE_VIDEO    = 5;
    static final int MSG_REPLACE_AUDIO    = 6;
    static final int MSG_PLAY_VIDEO       = 7;
    static final int MSG_PLAY_AUDIO       = 8;
    static final int MSG_VIEW_IMAGE       = 9;
    static final int MSG_REMOVE_ATTACHMENT = 10;
    /// M: add for attachment enhance
    static final int MSG_REMOVE_EXTERNAL_ATTACHMENT = 11;
    static final int MSG_REMOVE_SLIDES_ATTACHMENT = 12;

    private final Context mContext;
    private Handler mHandler;

    private SlideViewInterface mView;
    private SlideshowModel mSlideshow;
    private Presenter mPresenter;
    private boolean mCanSend;
    private Button mSendButton;
    private String mediaSizeUnit;

    /// M: add for vCard
    private View mFileAttachmentView;
    /// M: Compose Plugin for OP09@{
    private IMmsComposeExt mMmsComposePlugin = null;
    /// @}

    public AttachmentEditor(Context context, AttributeSet attr) {
        super(context, attr);
        mContext = context;
        initPlugin(context);
        mediaSizeUnit = mContext.getResources().getString(R.string.media_size_unit);//modify by wangingyue for mms ui
    }

    /**
     * Returns true if the attachment editor has an attachment to show.
     */
    public boolean update(WorkingMessage msg) {
        hideView();
        View tempView = (View) mView;
        mView = null;
        /// M: add for vcard @{
        mFileAttachmentView = null;
        mWorkingMessage = msg;
        /// @}
        // If there's no attachment, we have nothing to do.
        if (!msg.hasAttachment()) {
            return false;
        }

        // Get the slideshow from the message.
        mSlideshow = msg.getSlideshow();
        try {
            /// M: fix bug ALPS00947784, check and remove FileAttachment
            if (!MmsConfig.isSupportAttachEnhance()) {
                checkFileAttacment(msg);
            }
            /// M: for vcard: file attachment view and other views are exclusive to each other
            if (mSlideshow.sizeOfFilesAttach() > 0) {
                mFileAttachmentView = createFileAttachmentView(msg);
                if (mFileAttachmentView != null) {
                    mFileAttachmentView.setVisibility(View.VISIBLE);
                }
            }
            //add for attachment enhance
            if (mSlideshow.size() == 0) {
                //It only has attachment but not slide
                return true;
            }
            /// M: fix bug ALPS01238218
            if (mSlideshow.size() > 1 && !msg.getIsUpdateAttachEditor()) {
                MmsLog.d(TAG, "AttachmentEditor update, IsUpdateAttachEditor == false");
                if (tempView != null && tempView instanceof SlideshowAttachmentView) {
                    tempView.setVisibility(View.VISIBLE);
                }
                return true;
            }
            mView = createView(msg);
        } catch (IllegalArgumentException e) {
            return false;
        }

        if ((mPresenter == null) || !mSlideshow.equals(mPresenter.getModel())) {
            mPresenter = PresenterFactory.getPresenter(
                    "MmsThumbnailPresenter", mContext, mView, mSlideshow);
        } else {
            mPresenter.setView(mView);
        }

        if ((mPresenter != null) && mSlideshow.size() > 1) {
            mPresenter.present(null);
        } else if (mSlideshow.size() == 1) {
            SlideModel sm = mSlideshow.get(0);
            if ((mPresenter != null) && (sm != null) && (sm.hasAudio() || sm.hasImage() || sm.hasVideo())) {
                mPresenter.present(null);
            }
        }
        return true;
    }

    public void setHandler(Handler handler) {
        mHandler = handler;
    }
	/*HQ_zhangjing 2015-11-08 modified for CQ HQ01493261 begin*/
    public void hideSendBtn(boolean hideSendBtn) {
        if (null != mSendButton) {
			mSendButton.setVisibility( hideSendBtn ? View.VISIBLE:View.GONE);
        }
    }
	/*HQ_zhangjing 2015-11-08 modified for CQ HQ01493261 end*/
    public void setCanSend(boolean enable) {
        if (mCanSend != enable) {
            mCanSend = enable;
            updateSendButton();
        }
        /// M: for op09 Feature: dualSendButton;
        mMmsComposePlugin.setDualSendButtonType(dualBtnListener);
    }

    private void updateSendButton() {
        if (null != mSendButton) {
            if (mCanSend && MmsConfig.isSmsEnabled(mContext)) {
                mSendButton.setEnabled(true);
                mSendButton.setFocusable(true);
            } else {
                mSendButton.setEnabled(false);
                mSendButton.setFocusable(false);
            }
        }
        /// M: For Op09Feature: dualSendButton;
        mMmsComposePlugin.updateDualSendButtonStatue(mCanSend, true);
    }

    public void hideView() {
        if (mView != null) {
            ((View)mView).setVisibility(View.GONE);
        }
        /// M: add for vcard
        if (mFileAttachmentView != null) {
            mFileAttachmentView.setVisibility(View.GONE);
        }
    }

    private View getStubView(int stubId, int viewId) {
        View view = findViewById(viewId);
        if (view == null) {
            ViewStub stub = (ViewStub) findViewById(stubId);
            view = stub.inflate();
        }
        return view;
    }

    private class MessageOnClick implements OnClickListener {
        private int mWhat;

        public MessageOnClick(int what) {
            mWhat = what;
        }

        public void onClick(View v) {
            MmsLog.d(TAG, "AttachmentEditor onclick: mWhat = " + mWhat);
            Message msg = Message.obtain(mHandler, mWhat);
            msg.sendToTarget();
        }
    }

    /// M: private SlideViewInterface createView() {
    private SlideViewInterface createView(WorkingMessage msg) {

        boolean inPortrait = inPortraitMode();

        if (mSlideshow.size() > 1) {
            return createSlideshowView(inPortrait, msg);
        }

        final int NOT_OP01 = 0;
        final int IS_OP01 = 1;
        // 0 means not OP01, 1 means OP01
        int flag = NOT_OP01;
        if (MmsConfig.isSupportAttachEnhance()) {
            flag = IS_OP01;
        }

        SlideModel slide = mSlideshow.get(0);
        /// M: before using SlideModel's function,we should make sure it is null or not
        if (null == slide) {
            throw new IllegalArgumentException();
        }
        if (slide.hasImage()) {
            if (flag == NOT_OP01) {
                return createMediaView(R.id.image_attachment_view_stub, R.id.image_attachment_view,
                        R.id.view_image_button, R.id.replace_image_button,
                        R.id.remove_image_button,
                        /// M: MSG_VIEW_IMAGE, MSG_REPLACE_IMAGE, MSG_REMOVE_ATTACHMENT);
                        R.id.media_size_info, msg.getCurrentMessageSize(), MSG_VIEW_IMAGE,
                        MSG_REPLACE_IMAGE, MSG_REMOVE_ATTACHMENT, msg);
            } else {
                // OP01
                return createMediaView(R.id.image_attachment_view_stub, R.id.image_attachment_view,
                        R.id.view_image_button, R.id.replace_image_button,
                        R.id.remove_image_button, R.id.media_size_info,
                        msg.getCurrentMessageSize(), MSG_VIEW_IMAGE, MSG_REPLACE_IMAGE,
                        MSG_REMOVE_SLIDES_ATTACHMENT, msg);
            }
        } else if (slide.hasVideo()) {
            if (flag == NOT_OP01) {
                return createMediaView(R.id.video_attachment_view_stub, R.id.video_attachment_view,
                        R.id.view_video_button, R.id.replace_video_button,
                        R.id.remove_video_button,
                        /// M: MSG_PLAY_VIDEO, MSG_REPLACE_VIDEO, MSG_REMOVE_ATTACHMENT);
                        R.id.media_size_info, msg.getCurrentMessageSize(), MSG_PLAY_VIDEO,
                        MSG_REPLACE_VIDEO, MSG_REMOVE_ATTACHMENT, msg);
            } else {
                // OP01
                return createMediaView(R.id.video_attachment_view_stub, R.id.video_attachment_view,
                        R.id.view_video_button, R.id.replace_video_button,
                        R.id.remove_video_button, R.id.media_size_info,
                        msg.getCurrentMessageSize(), MSG_PLAY_VIDEO, MSG_REPLACE_VIDEO,
                        MSG_REMOVE_SLIDES_ATTACHMENT, msg);
            }
        } else if (slide.hasAudio()) {
            if (flag == NOT_OP01) {
                return createMediaView(R.id.audio_attachment_view_stub, R.id.audio_attachment_view,
                        R.id.play_audio_button, R.id.replace_audio_button,
                        R.id.remove_audio_button,
                        /// M: MSG_PLAY_AUDIO, MSG_REPLACE_AUDIO, MSG_REMOVE_ATTACHMENT);
                        R.id.media_size_info, msg.getCurrentMessageSize(), MSG_PLAY_AUDIO,
                        MSG_REPLACE_AUDIO, MSG_REMOVE_ATTACHMENT, msg);
            } else {
                // OP01
                return createMediaView(R.id.audio_attachment_view_stub, R.id.audio_attachment_view,
                        R.id.play_audio_button, R.id.replace_audio_button,
                        R.id.remove_audio_button, R.id.media_size_info,
                        msg.getCurrentMessageSize(), MSG_PLAY_AUDIO, MSG_REPLACE_AUDIO,
                        MSG_REMOVE_SLIDES_ATTACHMENT, msg);
            }
        } else {
            throw new IllegalArgumentException();
        }
    }


    /**
     * What is the current orientation?
     */
    private boolean inPortraitMode() {
        final Configuration configuration = mContext.getResources().getConfiguration();
        return configuration.orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    private SlideViewInterface createMediaView(
            int stub_view_id, int real_view_id,
            int view_button_id, int replace_button_id, int remove_button_id,
            /// M: @{
            // int viewMessage, int replaceMessage, int removeMessage) {
            int sizeViewId, int msgSize,
            int viewMessage, int replaceMessage, int removeMessage, WorkingMessage msg) {
            /// @}
        LinearLayout view = (LinearLayout)getStubView(stub_view_id, real_view_id);
        view.setVisibility(View.VISIBLE);

        Button viewButton = (Button) view.findViewById(view_button_id);
        Button replaceButton = (Button) view.findViewById(replace_button_id);
        Button removeButton = (Button) view.findViewById(remove_button_id);
        /// M: disable when non-default sms
        boolean smsEnable = MmsConfig.isSmsEnabled(mContext);
        replaceButton.setEnabled(smsEnable);
        removeButton.setEnabled(smsEnable);

        /// M: @{
        /// M: show Mms Size
        mMediaSize = (TextView) view.findViewById(sizeViewId);
        int sizeShow = (msgSize - 1) / 1024 + 1;
        String info = sizeShow + mediaSizeUnit+"/" + MmsConfig.getUserSetMmsSizeLimit(false) + mediaSizeUnit;//modify by wangingyue for mms ui
        mMediaSize.setText(info);
        /* HQ_sunli HQ01503603 20151117 begin*/
        //mMediaSize.setVisibility(View.GONE);
        /* HQ_sunli HQ01503603 20151117 end*/
        /// @}

        viewButton.setOnClickListener(new MessageOnClick(viewMessage));
        replaceButton.setOnClickListener(new MessageOnClick(replaceMessage));
        removeButton.setOnClickListener(new MessageOnClick(removeMessage));

        /// M: @{
        if (mFlagMini) {
            replaceButton.setVisibility(View.GONE);
        }
        /// @}
        return (SlideViewInterface) view;
    }

    /// M: @{
    // private SlideViewInterface createSlideshowView(boolean inPortrait) {
    private SlideViewInterface createSlideshowView(boolean inPortrait, WorkingMessage msg) {
    /// @}
        LinearLayout view =(LinearLayout) getStubView(
                R.id.slideshow_attachment_view_stub,
                R.id.slideshow_attachment_view);
        view.setVisibility(View.VISIBLE);

        Button editBtn = (Button) view.findViewById(R.id.edit_slideshow_button);
        mSendButton = (Button) view.findViewById(R.id.send_slideshow_button);
       /// M: @{
        mSendButton.setOnClickListener(new MessageOnClick(MSG_SEND_SLIDESHOW));
        /// @}

        updateSendButton();
        final ImageButton playBtn = (ImageButton) view.findViewById(
                R.id.play_slideshow_button);
        /// M: show Drm lock icon @{
        if (FeatureOption.MTK_DRM_APP && msg.mHasDrmPart) {
            MmsLog.i(TAG, "mHasDrmPart");
            Bitmap bitmap = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.mms_play_btn);
            Drawable front = mContext.getResources().getDrawable(
                    MessageResource.drawable.drm_red_lock);
            OmaDrmClient drmManager = MmsApp.getApplication().getDrmManagerClient();
            Bitmap drmBitmap = OmaDrmUiUtils.overlayBitmap(drmManager, bitmap, front);
            playBtn.setImageBitmap(drmBitmap);
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
                bitmap = null;
            }
        } else {
            playBtn.setImageResource(R.drawable.mms_play_btn);
        }

        /// M: show Mms Size
        mMediaSize = (TextView) view.findViewById(R.id.media_size_info);
               int sizeShow = (msg.getCurrentMessageSize() - 1) / 1024 + 1;
        String info = sizeShow + mediaSizeUnit+"/" + MmsConfig.getUserSetMmsSizeLimit(false) + mediaSizeUnit;//modify by wangingyue for mms ui
        mMediaSize.setText(info);
        /// @}

        editBtn.setEnabled(true);
        editBtn.setOnClickListener(new MessageOnClick(MSG_EDIT_SLIDESHOW));
        mSendButton.setOnClickListener(new MessageOnClick(MSG_SEND_SLIDESHOW));
        playBtn.setOnClickListener(new MessageOnClick(MSG_PLAY_SLIDESHOW));

        Button removeButton = (Button) view.findViewById(R.id.remove_slideshow_button);

        /// M: add for attachment enhance
        if (MmsConfig.isSupportAttachEnhance()) {
            // OP01
            removeButton.setOnClickListener(new MessageOnClick(MSG_REMOVE_SLIDES_ATTACHMENT));
        } else {
            // common
            removeButton.setOnClickListener(new MessageOnClick(MSG_REMOVE_ATTACHMENT));
        }
        /// M: For OP09;
        mMmsComposePlugin.initDualSendBtnForAttachment(mContext,
            (LinearLayout) findViewById(R.id.ct_button_slideshow_panel), mSendButton, dualBtnListener);
        /// M: disable when non-default sms
        boolean smsEnable = MmsConfig.isSmsEnabled(mContext);
        editBtn.setEnabled(smsEnable);
        removeButton.setEnabled(smsEnable);
        return (SlideViewInterface) view;
    }

    /// M:
    private WorkingMessage mWorkingMessage;
    private TextView mMediaSize;
    private TextView mFileAttachSize;
    private boolean mFlagMini = false;

    public void update(WorkingMessage msg, boolean isMini) {
        mFlagMini = isMini;
        update(msg);
    }

    public void onTextChangeForOneSlide() throws ExceedMessageSizeException {
        if (mWorkingMessage == null || mWorkingMessage.hasSlideshow()) {
            return;
        } else {
            /// M: fix bug ALPS01270248, update FileAttachment Size
            if (mFileAttachSize != null && mWorkingMessage.hasAttachedFiles() && mSlideshow != null) {
                if (!MmsConfig.isSupportAttachEnhance()) {
                    List<FileAttachmentModel> attachFiles = mSlideshow.getAttachFiles();
                    int attachSize = 0;
                    if (attachFiles != null && attachFiles.size() == 1) {
                        attachSize = attachFiles.get(0).getAttachSize();
                    }

                    int textSize = 0;
                    if (mSlideshow.get(0) != null && mSlideshow.get(0).hasText()) {
                        textSize = mSlideshow.get(0).getText().getMediaPackagedSize();
                    }
                    int totalSize = attachSize + textSize + SlideshowModel.mReserveSize;
                    String info = MessageUtils.getHumanReadableSize(totalSize)
                            + "/" + MmsConfig.getUserSetMmsSizeLimit(false) + mediaSizeUnit;//modify by wangingyue for mms ui
                    mFileAttachSize.setText(info);
                }
            }
        }

        if (mMediaSize == null) {
            return;
        }
        /// M: borrow this method to get the encoding type
        /// int[] params = SmsMessage.calculateLength(s, false);
        int totalSize = 0;
        if (mWorkingMessage.hasAttachment()) {
            totalSize = mWorkingMessage.getCurrentMessageSize();
        }
        /// M: show mms size
        int sizeShow = (totalSize - 1) / 1024 + 1;
        String info = sizeShow + mediaSizeUnit+"/" + MmsConfig.getUserSetMmsSizeLimit(false) + mediaSizeUnit;//modify by wangingyue for mms ui
        mMediaSize.setText(info);
    }

    /// M: add for vcard
    private View createFileAttachmentView(WorkingMessage msg) {
        /// M: for OP09;
        boolean isCtFeature = MmsConfig.isSupportVCardPreview();
        List<FileAttachmentModel> attachFiles = mSlideshow.getAttachFiles();

        /// M: add for attachment enhance
        if (attachFiles == null) {
            Log.e(TAG, "createFileAttachmentView, oops no attach files found.");
            return null;
        } else {
            if (!MmsConfig.isSupportAttachEnhance()) {
                // NOT for OP01
                if (attachFiles.size() != 1) {
                    return null;
                }
            }
        }

        FileAttachmentModel attach = attachFiles.get(0);
        Log.i(TAG, "createFileAttachmentView, attach " + attach.toString());
        final View view = getStubView(R.id.file_attachment_view_stub, R.id.file_attachment_view);
        view.setVisibility(View.VISIBLE);
        /// M: For OP09 @{
        ImageView thumb = (ImageView) view.findViewById(R.id.file_attachment_thumbnail);
        if (isCtFeature) {
            thumb.setVisibility(View.GONE);
            thumb = (ImageView) view.findViewById(R.id.file_attachment_thumbnail2);
            thumb.setVisibility(View.VISIBLE);
        }
        TextView name = (TextView) view.findViewById(R.id.file_attachment_name_info);
        TextView name2 = (TextView) view.findViewById(R.id.file_attachment_name_info2);
        /// @}
        String nameText = null;
        int thumbResId = -1;

        int attachSize = 0;
        //get external attachment size
        for (int i = 0; i < attachFiles.size(); i++) {
            attachSize += attachFiles.get(i).getAttachSize();
        }


       if (MmsConfig.isSupportAttachEnhance()) {
                //Op01
                /// M: add for attachment enhance, Op01 plugin
                if (attachFiles.size() > 1) {
                    // multi attachments files
                    MmsLog.i(TAG, "createFileAttachmentView, attachFiles.size() > 1");
                    nameText = mContext.getString(R.string.file_attachment_common_name,
                            String.valueOf(attachFiles.size()));
                    thumbResId = R.drawable.multi_files;
                } else if (attachFiles.size() == 1) {
                    // single attachment(file)
                    if (attach.isVCard()) {
                        // vCard
                        nameText = mContext.getString(R.string.file_attachment_vcard_name,
                                attach.getSrc());
                        thumbResId = R.drawable.ic_vcard_attach;
                    } else if (attach.isVCalendar()) {
                        // VCalender
                        nameText = mContext.getString(R.string.file_attachment_vcalendar_name,
                                attach.getSrc());
                        thumbResId = R.drawable.ic_vcalendar_attach;
                    } else {
                        // other attachment
                        nameText = attach.getSrc();
                        thumbResId = R.drawable.unsupported_file;
                    }
                }
            } else {
                // not op01
                if (attach.isVCard()) {
                    // / M: modify For OP09 {
                    if (isCtFeature) {
                        nameText = ((VCardModel) attach).getDisplayName();
                        if (TextUtils.isEmpty(nameText)) {
                            nameText = mContext.getString(R.string.file_attachment_vcard_name,
                                    attach.getSrc());
                        }
                        thumbResId = R.drawable.ipmsg_chat_contact_vcard;

                        if (isCtFeature && name2 != null) {
                            if (((VCardModel) attach).getContactCount() > 1) {
                                name2.setText(" +" + (((VCardModel) attach).getContactCount() - 1));
                                name2.setVisibility(View.VISIBLE);
                            }
                        }
                    } else {
                        nameText = mContext.getString(R.string.file_attachment_vcard_name,
                                attach.getSrc());
                        thumbResId = R.drawable.ic_vcard_attach;
                    }
                } else if (attach.isVCalendar()) {
                    nameText = mContext.getString(R.string.file_attachment_vcalendar_name,
                            attach.getSrc());
                    if (isCtFeature) {
                        thumbResId = R.drawable.ipmsg_chat_contact_calendar;
                    } else {
                        thumbResId = R.drawable.ic_vcalendar_attach;
                    }
                }
            }


        name.setText(nameText);
        /// M: Add for OP09@{
        if ((!isCtFeature || !attach.isVCard() || ((VCardModel) attach).getContactCount() <= 1)
                && name2 != null) {
            name2.setText("");
            name2.setVisibility(View.GONE);
        }
        /// @}
        thumb.setImageResource(thumbResId);
        final TextView size = (TextView) view.findViewById(R.id.file_attachment_size_info);
        mFileAttachSize = size;
        if (MmsConfig.isSupportAttachEnhance()) {
            // OP01
            size.setText(MessageUtils.getHumanReadableSize(attachSize));
        } else {
            // Not OP01
            /// M: fix bug ALPS01270248, update FileAttachment Size
            int textSize = 0;
            if (mSlideshow.get(0) != null && mSlideshow.get(0).hasText()) {
                textSize = mSlideshow.get(0).getText().getMediaPackagedSize();
            }
            int totalSize = attach.getAttachSize() + textSize + SlideshowModel.mReserveSize;
            String sizeUnit = mContext.getResources().getString(R.string.kilobyte);
            size.setText(MessageUtils.getHumanReadableSize(totalSize)
                + "/" + MmsConfig.getUserSetMmsSizeLimit(false) + sizeUnit);
        }

        final ImageView remove = (ImageView) view.findViewById(R.id.file_attachment_button_remove);
        final ImageView divider = (ImageView) view.findViewById(R.id.file_attachment_divider);
        divider.setVisibility(View.VISIBLE);
        remove.setVisibility(View.VISIBLE);

        if (MmsConfig.isSupportAttachEnhance()) {
            // OP01
            remove.setOnClickListener(new MessageOnClick(MSG_REMOVE_EXTERNAL_ATTACHMENT));
        } else {
            // not OP01
            remove.setOnClickListener(new MessageOnClick(MSG_REMOVE_ATTACHMENT));
        }
        /// M: disable when non-default sms
        boolean smsEnable = MmsConfig.isSmsEnabled(mContext);
        remove.setEnabled(smsEnable);
        return view;
    }

   /**
    * M: init plugin
    * @param context
    */
    private void initPlugin(Context context) {
        mMmsComposePlugin = (IMmsComposeExt) MPlugin.createInstance(
                IMmsComposeExt.class.getName(), context);
        if (mMmsComposePlugin == null) {
            mMmsComposePlugin = new DefaultMmsComposeExt(context);
            MmsLog.d(TAG, "default mMmsComposePlugin = " + mMmsComposePlugin);
        }
    }

    /// M: for op09Feature: DualSendBtn; the dual send button listener;
    ViewOnClickListener dualBtnListener = new ViewOnClickListener() {
        private int mSendSubId = -1;

        @Override
        public void onClick(View arg0) {
            Message msg = Message.obtain(mHandler, MSG_SEND_SLIDESHOW);
            Bundle data = new Bundle();
            data.putInt("send_sub_id", mSendSubId);
            msg.setData(data);
            msg.sendToTarget();
        }

        @Override
        public void setSelectedSubId(int subId) {
            mSendSubId = subId;
        }
    };

    /// M: fix bug ALPS00947784, check and remove FileAttachment
    private void checkFileAttacment(WorkingMessage msg) {
        if (msg.getSlideshow().sizeOfFilesAttach() > 0 && msg.hasMediaAttachments()) {
            msg.removeAllFileAttaches();
        }
    }
}
