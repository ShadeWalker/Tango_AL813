package com.android.mms;

import android.content.Context;

import com.android.mms.util.MmsLog;
import com.mediatek.mms.ext.DefaultMmsUnsupportedFilesExt;
import com.mediatek.mms.ext.IMmsUnsupportedFilesExt;
import com.mediatek.mms.ext.INmsgPluginExt;
import com.mediatek.mms.ext.DefaultNmsgPluginExt;
import com.mediatek.mms.ext.IMmsComposeExt;
import com.mediatek.mms.ext.DefaultMmsComposeExt;
import com.mediatek.mms.ext.DefaultAppGuideExt;
import com.mediatek.mms.ext.IAppGuideExt;
import com.mediatek.mms.ext.IMmsAskIfDownloadExt;
import com.mediatek.mms.ext.IMmsAttachmentEnhanceExt;
import com.mediatek.mms.ext.IMmsCancelDownloadExt;
import com.mediatek.mms.ext.IMmsDialogNotifyExt;
import com.mediatek.mms.ext.IMmsFailedNotifyExt;
import com.mediatek.mms.ext.IMmsSettingsExt;
import com.mediatek.mms.ext.IMmsTextSizeAdjustExt;
import com.mediatek.mms.ext.DefaultMmsSettingsExt;
import com.mediatek.mms.ext.IMmsUtilsExt;
import com.mediatek.mms.ext.ISmsReceiverExt;
import com.mediatek.mms.ext.IStringReplacementExt;
import com.mediatek.mms.ext.IUnreadMessageNumberExt;
import com.mediatek.mms.ext.DefaultMmsAskIfDownloadExt;
import com.mediatek.mms.ext.DefaultMmsAttachmentEnhanceExt;
import com.mediatek.mms.ext.DefaultMmsCancelDownloadExt;
import com.mediatek.mms.ext.DefaultMmsDialogNotifyExt;
import com.mediatek.mms.ext.DefaultMmsFailedNotifyExt;
import com.mediatek.mms.ext.DefaultMmsTextSizeAdjustExt;
import com.mediatek.mms.ext.DefaultMmsUtilsExt;
import com.mediatek.mms.ext.DefaultSmsReceiverExt;
import com.mediatek.mms.ext.DefaultStringReplacementExt;
import com.mediatek.mms.ext.DefaultUnreadMessageNumberExt;
import com.mediatek.mms.ext.IMmsTransactionExt;
import com.mediatek.mms.ext.DefaultMmsTransactionExt;
import com.mediatek.mms.ext.IMmsConversationExt;
import com.mediatek.mms.ext.DefaultMmsConversationExt;
import com.mediatek.mms.ext.IMmsPreferenceExt;
import com.mediatek.mms.ext.DefaultMmsPreferenceExt;
import com.mediatek.common.MPlugin;

/// M: add for ALPS01749707 for ipmessage plugin 
import com.mediatek.mms.ipmessage.IIpMessagePlugin;
import com.mediatek.mms.ipmessage.IpMessagePluginImpl;

public class MmsPluginManager {

    private static String TAG = "MmsPluginManager";

    public static final int MMS_PLUGIN_TYPE_DIALOG_NOTIFY = 0X0001;
    public static final int MMS_PLUGIN_TYPE_TEXT_SIZE_ADJUST = 0X0002;
    // M: fix bug ALPS00352897
    public static final int MMS_PLUGIN_TYPE_SMS_RECEIVER = 0X0003;

    public static final int MMS_PLUGIN_TYPE_MMS_ATTACHMENT_ENHANCE = 0X0005;
    ///M: add for Mms transaction plugin
    public static final int MMS_PLUGIN_TYPE_MMS_TRANSACTION = 0X0007;

    public static final int MMS_PLUGIN_TYPE_MMS_COMPOSE = 0X0008;

    public static final int MMS_PLUGIN_TYPE_MMS_SETTINGS = 0X0009;

    ///M: add for OP09 @{
    public static final int MMS_PLUGIN_TYPE_FAILED_NOTIFY = 0X000b;
    public static final int MMS_PLUGIN_TYPE_CANCEL_DOWNLOAD = 0X0000c;
    public static final int MMS_PLUGIN_TYPE_MESSAGE_UTILS = 0X000d;
    public static final int MMS_PLUGIN_TYPE_ASK_IF_DOWNLOAD = 0X0000e;
    public static final int MMS_PLUGIN_TYPE_STRING_REPLACEMENT = 0X000f;
    public static final int MMS_PLUGIN_TYPE_UNREAD_MESSAGE = 0X0010;
    public static final int MMS_PLUGIN_TYPE_UNSUPPORTED_FILES = 0x0100;
    /// @}

    public static final int MMS_PLUGIN_TYPE_MMS_CONV = 0X0013;
    ///M: add for preference plugin
    public static final int MMS_PLUGIN_TYPE_PREFERENCE = 0x0021;

    /// M: add for ALPS01749707 for ipmessage plugin
    public static final int MMS_PLUGIN_TYPE_IPMSG_PLUGIN = 0X0031;

    private static IMmsTextSizeAdjustExt mMmsTextSizeAdjustPlugin = null;
    private static IMmsDialogNotifyExt mMmsDialogNotifyPlugin = null;
    // M: fix bug ALPS00352897
    private static ISmsReceiverExt mSmsReceiverPlugin = null;
    private static IAppGuideExt mAppGuideExt = null;
    public static final int MMS_PLUGIN_TYPE_APPLICATION_GUIDE = 0X0004;
    private static IMmsAttachmentEnhanceExt mMmsAttachmentEnhancePlugin = null;
    ///M: add for Mms transaction plugin
    private static IMmsTransactionExt mMmsTransactionPlugin = null;
    private static IMmsComposeExt mMmsComposePlugin = null;
    private static IMmsSettingsExt mMmsSettingsPlugin = null;

    /// M: New member for OP09 plug-in @{
    private static IMmsFailedNotifyExt sMmsFailedNotifyPlugin = null;
    private static IMmsCancelDownloadExt sCancelDownloadPlugin = null;
    private static IMmsAskIfDownloadExt sAskIfDownloadPlugin = null;
    private static IMmsUtilsExt sMmsUtilsPlugin = null;
    private static IStringReplacementExt sStringReplacementPlugin = null;
    private static IUnreadMessageNumberExt sUnreadMessagePlugin = null;
    /// @}

    ///@}
    private static IMmsConversationExt mMmsConversationPlugin = null;
    private static IMmsPreferenceExt mMmsPreferencePlugin = null;
    /// M: add for ALPS01766374 for ipmessage plugin
    private static INmsgPluginExt mNmsgPlugin = null;

    /// M: OP09 Feature: UNsupportedFiles;
    private static IMmsUnsupportedFilesExt sMmsUnsupportedFilesPlugin = null;

    public static void initPlugins(Context context) {

        //Dialog Notify
        mMmsDialogNotifyPlugin = (IMmsDialogNotifyExt) MPlugin.createInstance(IMmsDialogNotifyExt.class.getName(), context);
        if (mMmsDialogNotifyPlugin == null) {
            mMmsDialogNotifyPlugin = new DefaultMmsDialogNotifyExt(context);
            MmsLog.d(TAG, "operator mMmsDialogNotifyPlugin = " + mMmsDialogNotifyPlugin);
        }

        //TextSizeAdjust plugin
        mMmsTextSizeAdjustPlugin = (IMmsTextSizeAdjustExt) MPlugin.createInstance(IMmsTextSizeAdjustExt.class.getName(), context);
        if (mMmsTextSizeAdjustPlugin == null) {
            mMmsTextSizeAdjustPlugin = new DefaultMmsTextSizeAdjustExt(context);
            MmsLog.d(TAG, "default mMmsTextSizeAdjustPlugin = " + mMmsTextSizeAdjustPlugin);
        }

        // M: fix bug ALPS00352897
        //SmsReceiver plugin
         mSmsReceiverPlugin = (ISmsReceiverExt) MPlugin.createInstance(ISmsReceiverExt.class.getName(), context);
         if (mSmsReceiverPlugin == null) {
            mSmsReceiverPlugin = new DefaultSmsReceiverExt(context);
            MmsLog.d(TAG, "default mSmsReceiverPlugin = " + mSmsReceiverPlugin);
        }

        /// M: add for application guide. @{
        mAppGuideExt = (IAppGuideExt) MPlugin.createInstance(IAppGuideExt.class.getName(), context);
        if (mAppGuideExt == null) {
            mAppGuideExt = new DefaultAppGuideExt();
            MmsLog.d(TAG, "default mAppGuideExt = " + mAppGuideExt);
        }
        /// @}

        //Mms attachment enhance plugin
         mMmsAttachmentEnhancePlugin =
            (IMmsAttachmentEnhanceExt) MPlugin.createInstance(IMmsAttachmentEnhanceExt.class.getName(), context);
        if (mMmsAttachmentEnhancePlugin == null) {
            mMmsAttachmentEnhancePlugin = new DefaultMmsAttachmentEnhanceExt(context);
            MmsLog.d(TAG, "default mMmsAttachmentEnhancePlugin = " + mMmsAttachmentEnhancePlugin);
        }

        ///M: add for Mms transaction plugin
        mMmsTransactionPlugin = (IMmsTransactionExt) MPlugin.createInstance(IMmsTransactionExt.class.getName(), context);
        if (mMmsTransactionPlugin == null) {
            mMmsTransactionPlugin = new DefaultMmsTransactionExt(context);
            MmsLog.d(TAG, "default mMmsTransactionPlugin = " + mMmsTransactionPlugin);
        }
        ///@}

        /// M: add for Mms Compose plugin
        mMmsComposePlugin = (IMmsComposeExt) MPlugin.createInstance(IMmsComposeExt.class.getName(), context);
        if (mMmsComposePlugin == null) {
            mMmsComposePlugin = new DefaultMmsComposeExt(context);
            MmsLog.d(TAG, "default mMmsComposePlugin = " + mMmsComposePlugin);
        }

        ///M: add for regional phone plugin @{
        mMmsSettingsPlugin = (IMmsSettingsExt) MPlugin.createInstance(IMmsSettingsExt.class.getName(), context);
        if (mMmsSettingsPlugin == null) {
            mMmsSettingsPlugin = new DefaultMmsSettingsExt(context);
            MmsLog.d(TAG, "default mMmsSettingsPlugin = " + mMmsSettingsPlugin);
        }
        ///@}

        /// M: add for OP09 feature @{
        sMmsFailedNotifyPlugin = (IMmsFailedNotifyExt) MPlugin.createInstance(IMmsFailedNotifyExt.class.getName(), context);
        if (sMmsFailedNotifyPlugin == null) {
            sMmsFailedNotifyPlugin = new DefaultMmsFailedNotifyExt(context);
            MmsLog.d(TAG, "default sMmsFailedNotifyPlugin = " + sMmsFailedNotifyPlugin);
        }

        sCancelDownloadPlugin = (IMmsCancelDownloadExt) MPlugin.createInstance(IMmsCancelDownloadExt.class.getName(), context);
        if (sCancelDownloadPlugin == null) {
            sCancelDownloadPlugin = new DefaultMmsCancelDownloadExt(context);
            MmsLog.d(TAG, "default sCancelDownloadPlugin = " + sCancelDownloadPlugin);
        }

        sAskIfDownloadPlugin = (IMmsAskIfDownloadExt) MPlugin.createInstance(IMmsAskIfDownloadExt.class.getName(), context);
        if (sAskIfDownloadPlugin == null) {
            sAskIfDownloadPlugin = new DefaultMmsAskIfDownloadExt(context);
            MmsLog.d(TAG, "default sAskIfDownloadPlugin = " + sAskIfDownloadPlugin);
        }

        sMmsUtilsPlugin = (IMmsUtilsExt) MPlugin.createInstance(IMmsUtilsExt.class.getName(), context);
        if (sMmsUtilsPlugin == null) {
            sMmsUtilsPlugin = new DefaultMmsUtilsExt(context);
            MmsLog.d(TAG, "default sMmsUtilsPlugin = " + sMmsUtilsPlugin);
        }

        sStringReplacementPlugin = (IStringReplacementExt) MPlugin.createInstance(IStringReplacementExt.class.getName(), context);
        if (sStringReplacementPlugin == null) {
            sStringReplacementPlugin = new DefaultStringReplacementExt(context);
            MmsLog.d(TAG, "default sStringReplacementPlugin = " + sStringReplacementPlugin);
        }

        sUnreadMessagePlugin = (IUnreadMessageNumberExt) MPlugin.createInstance(IUnreadMessageNumberExt.class.getName(), context);
        if (sUnreadMessagePlugin == null) {
            sUnreadMessagePlugin = new DefaultUnreadMessageNumberExt(context.getApplicationContext());
            MmsLog.d(TAG, "default sUnreadMessagePlugin = " + sUnreadMessagePlugin);
        }
        ///@}

        mMmsConversationPlugin = (IMmsConversationExt) MPlugin.createInstance(IMmsConversationExt.class.getName(), context);
        if (mMmsConversationPlugin == null) {
            mMmsConversationPlugin = new DefaultMmsConversationExt(context);
            MmsLog.d(TAG, "default mMmsConversationPlugin = " + mMmsConversationPlugin);
        }

        mMmsPreferencePlugin = (IMmsPreferenceExt) MPlugin.createInstance(IMmsPreferenceExt.class.getName(), context);
        if (mMmsPreferencePlugin == null) {
            mMmsPreferencePlugin = new DefaultMmsPreferenceExt(context);
            MmsLog.d(TAG, "default mMmsPreferencePlugin = " + mMmsPreferencePlugin);
        }
        /// M: add for ALPS01766374 for ipmessage plugin @{
        mNmsgPlugin = (INmsgPluginExt) MPlugin.createInstance(INmsgPluginExt.class.getName(),context);
        if(mNmsgPlugin == null) {
        	mNmsgPlugin = new DefaultNmsgPluginExt(context);
            MmsLog.d(TAG, "default DefaultNmsgPluginExt = " + mNmsgPlugin);
        }
        ///@}

        /// M: OP09 Feature: Unsupported Files; @{
        sMmsUnsupportedFilesPlugin = (IMmsUnsupportedFilesExt) MPlugin.createInstance(
            IMmsUnsupportedFilesExt.class.getName(), context);
        if (sMmsUnsupportedFilesPlugin == null) {
            sMmsUnsupportedFilesPlugin = new DefaultMmsUnsupportedFilesExt(context);
            MmsLog.d(TAG, "default DefaultMmsUnsupportedFilesExt = " + sMmsUnsupportedFilesPlugin);
        }
        /// @}
    }

    public static Object getMmsPluginObject(int type) {
        Object obj = null;
        MmsLog.d(TAG, "getMmsPlugin, type = " + type);
        switch(type) {

            case MMS_PLUGIN_TYPE_DIALOG_NOTIFY:
                obj = mMmsDialogNotifyPlugin;
                break;

            case MMS_PLUGIN_TYPE_TEXT_SIZE_ADJUST:
                obj = mMmsTextSizeAdjustPlugin;
                break;

            // M: fix bug ALPS00352897
            case MMS_PLUGIN_TYPE_SMS_RECEIVER:
                obj = mSmsReceiverPlugin;
                break;
            case MMS_PLUGIN_TYPE_MMS_ATTACHMENT_ENHANCE:
                obj = mMmsAttachmentEnhancePlugin;
                break;

            case MMS_PLUGIN_TYPE_APPLICATION_GUIDE:
                obj = mAppGuideExt;
                break;

            ///M: add for Mms transaction plugin
            case MMS_PLUGIN_TYPE_MMS_TRANSACTION:
                obj = mMmsTransactionPlugin;
                break;
            ///@}
            case MMS_PLUGIN_TYPE_MMS_COMPOSE:
                obj = mMmsComposePlugin;
                break;
            case MMS_PLUGIN_TYPE_MMS_SETTINGS:
                obj = mMmsSettingsPlugin;
                break;

             /// M: add for OP09 feature. @{
             case MMS_PLUGIN_TYPE_FAILED_NOTIFY:
                 obj = sMmsFailedNotifyPlugin;
                 break;

             case MMS_PLUGIN_TYPE_CANCEL_DOWNLOAD:
                 obj = sCancelDownloadPlugin;
                 break;

             case MMS_PLUGIN_TYPE_MESSAGE_UTILS:
                 obj = sMmsUtilsPlugin;
                 break;

             case MMS_PLUGIN_TYPE_ASK_IF_DOWNLOAD:
                 obj = sAskIfDownloadPlugin;
                 break;

             case MMS_PLUGIN_TYPE_STRING_REPLACEMENT:
                 obj = sStringReplacementPlugin;
                 break;

             case MMS_PLUGIN_TYPE_UNREAD_MESSAGE:
                 obj = sUnreadMessagePlugin;
                 break;
            /// @}

            ///M: add for MMS conversation plugin @{
            case MMS_PLUGIN_TYPE_MMS_CONV:
                obj = mMmsConversationPlugin;
                break;
            ///@}

            case MMS_PLUGIN_TYPE_PREFERENCE:
                obj = mMmsPreferencePlugin;
                break;

            /// M: add for ALPS01766374 for ipmessage plugin @{
            case  MMS_PLUGIN_TYPE_IPMSG_PLUGIN:
              obj = mNmsgPlugin;
             break;
            ///@}

             /// M: OP09 Feature: Unpported Files; @{
            case MMS_PLUGIN_TYPE_UNSUPPORTED_FILES:
                obj = sMmsUnsupportedFilesPlugin;
                break;
           /// @}

            default:
                MmsLog.e(TAG, "getMmsPlugin, type = " + type + " don't exist");
                break;
        }
        return obj;

    }
}
