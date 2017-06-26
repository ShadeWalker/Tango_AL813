/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
package com.mediatek.contacts.util;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.content.ComponentName;
import android.os.Build;
import android.os.UserHandle;
import android.provider.Settings;
import android.net.Uri;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.mediatek.contacts.ExtensionManager;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.internal.telephony.uicc.SvlteUiccUtils;
import com.mediatek.telecom.TelecomManagerEx;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.widget.CustomAccountRemoteViews.AccountInfo;
import com.mediatek.widget.DefaultAccountSelectionBar;

import java.util.ArrayList;
import java.util.List;

public class SetIndicatorUtils {
    private static final String TAG = "SetIndicatorUtils";

    private static final String PEOPLEACTIVITY = "com.android.contacts.activities.PeopleActivtiy";
    private static final String QUICKCONTACTACTIVITY = "com.android.contacts.quickcontact.QuickContactActivity";
    private static final String INDICATE_TYPE = "CONTACTS";
    private static final String ACTION_OUTGOING_CALL_PHONE_ACCOUNT_CHANGED = "com.android.contacts.ACTION_OUTGOING_CALL_PHONE_ACCOUNT_CHANGED";
    private static final String EXTRA_ACCOUNT = "extra_account";

    private static SetIndicatorUtils sInstance = null;
    private DefaultAccountSelectionBar mDefaultAccountSelectionBar = null;
    private boolean mShowSimIndicator = false;
    private BroadcastReceiver mReceiver = null;

    // In PeopleActivity, if quickContact is show, quickContactIsShow = true,
    // PeopleActivity.onPause cannot hide the Indicator.
    private boolean mQuickContactIsShow = false;
    private Activity mActivity = null;
    private boolean mIsRegister = false;
    private ComponentName mComponentName;

    public static SetIndicatorUtils getInstance() {
        if (sInstance == null) {
            sInstance = new SetIndicatorUtils();
        }
        return sInstance;
    }

    public void showIndicator(Activity activity, boolean visible) {
        if (UserHandle.myUserId() != UserHandle.USER_OWNER) {
            //None user owner don't show notification bar.
            return;
        }

        LogUtils.i(TAG, "[showIndicator]visible : " + visible);
        mActivity = activity;
        mShowSimIndicator = visible;

        if (mDefaultAccountSelectionBar == null) {
            Context context = mActivity;
            mDefaultAccountSelectionBar = new DefaultAccountSelectionBar(context,
                        context.getPackageName(), null);
            mComponentName = activity.getComponentName();
        }

        setSimIndicatorVisibility(visible);
    }

    public void registerReceiver(Activity activity) {
        LogUtils.d(TAG, "[register] activity : " + activity + ",register:" + mIsRegister);
        if (!mIsRegister) {
            if (mReceiver == null) {
                mReceiver = new MyBroadcastReceiver();
            }

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_OUTGOING_CALL_PHONE_ACCOUNT_CHANGED);
            intentFilter.addAction(TelecomManagerEx.ACTION_PHONE_ACCOUNT_CHANGED);
            activity.registerReceiver(mReceiver, intentFilter);
            mIsRegister = true;
        }
    }

    public void unregisterReceiver(Activity activity) {
        LogUtils.d(TAG, "[unregister] activity : " + activity + ",unregister:" + mIsRegister);
        if (mIsRegister) {
            activity.unregisterReceiver(mReceiver);
            mIsRegister = false;
            /// M: Clear the instance so we can get a new one when Activity recreated. 
            sInstance = null;
        }
    }

    private SetIndicatorUtils() {

    }

    private void setSimIndicatorVisibility(boolean visible) {
        if (visible) {
            List<AccountInfo> accountList = getPhoneAccountInfos(mActivity);
            Log.d(TAG, "[setSimIndicatorVisibility] accountList size " + accountList.size());

            /* M: [Solution2.0]If is C2K C+C case, don't show account select notification @{*/
            if (isCdmaCardCompetion(mActivity)) {
                mDefaultAccountSelectionBar.hide();
                hideSIMIndicatorAtStatusbar(mActivity, mComponentName);
                mShowSimIndicator = false;
            /* @}*/
            } else if (accountList.size() > 2) {
                mDefaultAccountSelectionBar.updateData(accountList);
                mDefaultAccountSelectionBar.show();
                showSIMIndicatorAtStatusbar(mActivity, mComponentName);
            } else {
                mDefaultAccountSelectionBar.hide();
                hideSIMIndicatorAtStatusbar(mActivity, mComponentName);
                mShowSimIndicator = false;
            }
            registerReceiver(mActivity);

        } else {
            mDefaultAccountSelectionBar.hide();
            mShowSimIndicator = false;
            unregisterReceiver(mActivity);
            hideSIMIndicatorAtStatusbar(mActivity, mComponentName);
        }
    }

    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_OUTGOING_CALL_PHONE_ACCOUNT_CHANGED.equals(action)) {

                if (mShowSimIndicator) {
                    updateSelectedAccount(intent);
                    setSimIndicatorVisibility(true);
                }
                hideNotification();
            } else if (TelecomManagerEx.ACTION_PHONE_ACCOUNT_CHANGED.equals(action)) {
                setSimIndicatorVisibility(true);
            }
        }
    }

    private void hideNotification() {
        Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        mActivity.sendBroadcast(intent);
    }

    private List<AccountInfo> getPhoneAccountInfos(Context context) {
        TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        List<PhoneAccountHandle> accountHandles = telecomManager.getCallCapablePhoneAccounts();

        Log.d(TAG, "[getPhoneAccountInfos] accountHandles.size" + accountHandles.size());
        List<AccountInfo> accountInfos = new ArrayList<AccountInfo>();
        PhoneAccountHandle selectedAccountHandle = telecomManager.getUserSelectedOutgoingPhoneAccount();
        // Add the always ask item
        AccountInfo alwaysAskInfo = createAlwaysAskAccountInfo(context, selectedAccountHandle == null);
        accountInfos.add(alwaysAskInfo);

        for (PhoneAccountHandle handle : accountHandles) {
            final PhoneAccount account = telecomManager.getPhoneAccount(handle);
            if (account == null) {
                continue;
            }

            String label = account.getLabel() != null ? account.getLabel().toString() : null;
            Uri sddress = account.getAddress();
            Uri subAddress = account.getSubscriptionAddress();
            String number = null;

            if (subAddress != null) {
                number = subAddress.getSchemeSpecificPart();
            } else if (sddress != null) {
                number = sddress.getSchemeSpecificPart();
            }

            Intent intent = new Intent(ACTION_OUTGOING_CALL_PHONE_ACCOUNT_CHANGED);
            intent.putExtra(EXTRA_ACCOUNT, handle);
            boolean isSelected = false;

            if (handle.equals(selectedAccountHandle)) {
                isSelected = true;
            }

            AccountInfo info = new AccountInfo(account.getIconResId(), drawableToBitmap(account
                    .createIconDrawable(context)), label, number, intent, isSelected);
            if (Build.TYPE.equals("eng")) {
                Log.d(TAG, "label =" + label + ", number =" + number);
            }
            accountInfos.add(info);
        }
        return accountInfos;
    }

    private AccountInfo createAlwaysAskAccountInfo(Context context, boolean isSelected) {
        Intent intent = new Intent(ACTION_OUTGOING_CALL_PHONE_ACCOUNT_CHANGED);
        String label = context.getString(com.mediatek.R.string.account_always_ask_title);
        int iconResId = ExtensionManager.getInstance().getCtExtension()
                .showAlwaysAskIndicate(com.mediatek.R.drawable.account_always_ask_icon);
        Log.i(TAG, "[createAlwaysAskAccountInfo] iconResId : " + iconResId);
        return new AccountInfo(iconResId, null, label, null, intent, isSelected);
        }

    private void updateSelectedAccount(Intent intent) {
        PhoneAccountHandle handle = (PhoneAccountHandle) intent.getParcelableExtra(EXTRA_ACCOUNT);
        Context context = mActivity;
        TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);

        telecomManager.setUserSelectedOutgoingPhoneAccount(handle);
    }

    /**
     * DefaultAccountSelectionBar only accept the bitmap,
     * so if drawable, need covert it to bitmap.
     * @param drawable the original drawable.
     * @return the converted bitmap.
     */
    private Bitmap drawableToBitmap(Drawable drawable) {
        BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
        return bitmapDrawable.getBitmap();
    }

    /**
     * Show the under line indicator for default SIM
     * @param context the Context
     * @param componentName the ComponentName of the caller app
     */
    public static void showSIMIndicatorAtStatusbar(Context context, ComponentName componentName) {
        TelecomManager telecomManager = (TelecomManager) context
                .getSystemService(Context.TELECOM_SERVICE);
        PhoneAccountHandle selectedAccountHandle = telecomManager
                .getUserSelectedOutgoingPhoneAccount();
        PhoneAccount account = telecomManager.getPhoneAccount(selectedAccountHandle);
        ComponentName sipComponentName = new ComponentName("com.android.phone",
                "com.android.services.telephony.sip.SipConnectionService");
        StatusBarManager statusbar = (StatusBarManager) context
                .getSystemService(Context.STATUS_BAR_SERVICE);
        if (selectedAccountHandle == null) {
            // Call Statusbar api to show always ask (-1)
            statusbar.showSimIndicator(componentName, Settings.System.VOICE_CALL_SIM_SETTING,
                    Settings.System.DEFAULT_SIM_SETTING_ALWAYS_ASK);
        } else if (account != null
                && account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                && TextUtils.isDigitsOnly(selectedAccountHandle.getId())) {
            // It is SIM phone account
            long subId = Long.parseLong(selectedAccountHandle.getId());
            // Call Statusbar api to Show sim indicator with SubId
            statusbar.showSimIndicator(componentName, Settings.System.VOICE_CALL_SIM_SETTING,
                    subId);
        } else if (selectedAccountHandle.getComponentName().equals(sipComponentName)) {
            // It is SIP phone account
            // Call Statusbar api to Show sim indicator with SIP id (-2)
            statusbar.showSimIndicator(componentName, Settings.System.VOICE_CALL_SIM_SETTING,
                    Settings.System.VOICE_CALL_SIM_SETTING_INTERNET);
        } else {
            // Call Statusbar api to hide sim indicator
            statusbar.hideSimIndicator(componentName);
        }
    }

    /**
     * Hide the under line indicator for default SIM
     * @param context the Context
     * @param componentName the ComponentName of the caller app
     */
    public static void hideSIMIndicatorAtStatusbar(Context context, ComponentName componentName) {
        // Call Statusbar api to hide sim indicator
        StatusBarManager statusbar = (StatusBarManager) context
                .getSystemService(Context.STATUS_BAR_SERVICE);
        statusbar.hideSimIndicator(componentName);
    }

    /**
     * For C2K C+C case, only one SIM card register network, other card can recognition.
     * and can not register the network
     * 1. two CDMA cards.
     * 2. two cards is competitive. only one modem can register CDMA network.
     * @param context
     * @return
     */
    public static boolean isCdmaCardCompetion(Context context) {
        boolean isCdmaCard = false;
        boolean isCompetition = false;
        int simCount = TelephonyManager.from(context).getSimCount();

        for (int slotId = 0; slotId < simCount && simCount >= 2; slotId++) {
            isCdmaCard =
                    SvlteUiccUtils.getInstance().getSimType(slotId) == SvlteUiccUtils.SIM_TYPE_CDMA;
            int[] subId = SubscriptionManager.getSubId(slotId);
            if (subId != null && subId.length > 0) {
                isCompetition =TelephonyManagerEx.getDefault().isInHomeNetwork(subId[0]);
            }
        }
        Log.d(TAG, "isCdmaCard: " + isCdmaCard +
                " isCompletition: " + isCompetition + "simCount" + simCount);
        return isCdmaCard && isCompetition;
    }
}
