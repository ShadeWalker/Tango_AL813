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
package com.mediatek.dialer.util;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.RadioAccessFamily;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.android.dialer.DialerApplication;
import com.android.dialer.R;
import com.android.dialer.calllog.PhoneAccountUtils;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.mediatek.contacts.util.SetIndicatorUtils;
import com.mediatek.dialer.ext.ExtensionManager;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.internal.telephony.uicc.SvlteUiccUtils;
import com.mediatek.telecom.TelecomManagerEx;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.widget.CustomAccountRemoteViews.AccountInfo;
import com.mediatek.widget.DefaultAccountSelectionBar;

import java.util.ArrayList;
import java.util.List;

/// M: [Call Account Notification] Show a notification to indicator the available call accounts
/// and the selected call account. And allow the user to select the default call account.
public class CallAccountSelectionNotificationUtil {
    private static final String TAG = "CallAccountSelectionNotificationUtil";

    private static final String INDICATE_TYPE = "CONTACTS";
    private static final String ACTION_OUTGOING_CALL_PHONE_ACCOUNT_CHANGED
            = "com.android.dialer.ACTION_OUTGOING_CALL_PHONE_ACCOUNT_CHANGED";
    private static final String EXTRA_ACCOUNT = "extra_account";
    private static final String PROPERTY_3G_SIM = "persist.radio.simswitch";

    private static CallAccountSelectionNotificationUtil sInstance = null;
    private DefaultAccountSelectionBar mDefaultAccountSelectionBar = null;
    private boolean mNotificationShown = false;
    private BroadcastReceiver mReceiver = null;
    private ComponentName mComponentName;

    public static CallAccountSelectionNotificationUtil getInstance(Activity activity) {
        if (sInstance == null) {
            sInstance = new CallAccountSelectionNotificationUtil(activity);
        }
        return sInstance;
    }

    private CallAccountSelectionNotificationUtil(Activity activity) {
        if (mDefaultAccountSelectionBar == null) {
            Context context = DialerApplication.getDialerContext();
            mDefaultAccountSelectionBar = new DefaultAccountSelectionBar(activity, context.getPackageName(), null);
            mComponentName = activity.getComponentName();
        }
    }

    public void showNotification(boolean visible, Activity activity) {
        if (!DialerFeatureOptions.CALL_ACCOUNT_NOTIFICATION) {
            return;
        }

        if (UserHandle.myUserId() != UserHandle.USER_OWNER) {
            LogUtils.i(TAG, "[showNotification] Normal users are not allowed" +
                    " to change default phone account");
            return;
        }

        LogUtils.i(TAG, "[showNotification]visible : " + visible);
        mNotificationShown = visible;

        setAccountNotificationVisibility(visible, activity);
        /** M: [ALPS01823868] handle Receiver according to the visibility @{ */
        if (visible) {
            registerReceiver(activity);
        } else {
            unregisterReceiver(activity);
        }
        /** @} */
    }

    private void setAccountNotificationVisibility(boolean visible, Context context) {
        if (visible) {
            List<AccountInfo> accountList = getPhoneAccountInfos(context);
            // Show while there are more than one phone accounts + "Ask first"
            if (accountList.size() > 2) {
                /* For solution2 C+C case, two CDMA cards can be recognized,
                 * but only one of them can register network.
                 * Hide account notification if two CDMA cards are inserted.
                 */
                if (!isTwoCdmaCardAndInHomeNetwork(context)) {
                    mDefaultAccountSelectionBar.updateData(accountList);
                    mDefaultAccountSelectionBar.show();
                    mNotificationShown = true;
                }
                SetIndicatorUtils.showSIMIndicatorAtStatusbar(context, mComponentName);
            } else {
                mDefaultAccountSelectionBar.hide();
                SetIndicatorUtils.hideSIMIndicatorAtStatusbar(context, mComponentName);
                mNotificationShown = false;
            }
        } else {
            mDefaultAccountSelectionBar.hide();
            SetIndicatorUtils.hideSIMIndicatorAtStatusbar(context, mComponentName);
            mNotificationShown = false;
        }
    }

    public void registerReceiver(Context context) {
        if (mReceiver == null) {
            mReceiver = new MyBroadcastReceiver();
            /** M: [ALPS01823868] don't register duplicated receiver @{ */
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_OUTGOING_CALL_PHONE_ACCOUNT_CHANGED);
            intentFilter.addAction(TelecomManagerEx.ACTION_PHONE_ACCOUNT_CHANGED);
            context.registerReceiver(mReceiver, intentFilter);
            /** @} */
        }
    }

    public void unregisterReceiver(Context context) {
        if (mReceiver != null) {
            context.unregisterReceiver(mReceiver);
        }
        mReceiver = null;
        sInstance = null;
    }

    private List<AccountInfo> getPhoneAccountInfos(Context context) {
        TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        List<PhoneAccountHandle> accountHandles = telecomManager.getCallCapablePhoneAccounts();
        List<AccountInfo> accountInfos = new ArrayList<AccountInfo>();
        PhoneAccountHandle selectedAccountHandle = telecomManager.getUserSelectedOutgoingPhoneAccount();
        // Add the always ask item
        AccountInfo alwaysAskInfo = createAlwaysAskAccountInfo(context, selectedAccountHandle == null);
        accountInfos.add(alwaysAskInfo);
        // Add the call capable phone accounts
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
            AccountInfo info = new AccountInfo(account.getIconResId(), drawableToBitmap(account.createIconDrawable(context)),
                    label, number, intent, isSelected);
            accountInfos.add(info);
        }
        return accountInfos;
    }

    private AccountInfo createAlwaysAskAccountInfo(Context context, boolean isSelected) {
        Intent intent = new Intent(ACTION_OUTGOING_CALL_PHONE_ACCOUNT_CHANGED);
        String label = context.getString(com.mediatek.R.string.account_always_ask_title);
        int iconId = com.mediatek.R.drawable.account_always_ask_icon;

        /// M: for Plug-in @{
        iconId = ExtensionManager.getInstance().getSelectAccountExtension().getAlwaysAskAccountIcon(iconId);
        /// @}

        AccountInfo alwaysAskAccountInfo = new AccountInfo(iconId, null, label, null, intent, isSelected);

        return alwaysAskAccountInfo;
    }

    private void updateSelectedAccount(Intent intent) {
        PhoneAccountHandle handle = (PhoneAccountHandle) intent.getParcelableExtra(EXTRA_ACCOUNT);
        Context context = DialerApplication.getDialerContext();
        TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        telecomManager.setUserSelectedOutgoingPhoneAccount(handle);
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bm = null;
        if (drawable != null) {
            BitmapDrawable bd = (BitmapDrawable) drawable;
            bm = bd.getBitmap();
        }
        LogUtils.d(TAG, "--- drawable is null ---");

        return bm;
    }

    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            LogUtils.i(TAG, "[onReceive] action = " + action);

            if (ACTION_OUTGOING_CALL_PHONE_ACCOUNT_CHANGED.equals(action)) {
                LogUtils.i(TAG, "[onReceive]mNotificationShown : " + mNotificationShown);

                /* For solution1.5 C+G case, only support one CDMA card, when GSM card has
                 * 4G capability, the CDMA card can't register network.
                 * When switch call account to CDMA card, need switch 3G/4G capability to CDMA card.
                 */
                if (!DialerFeatureOptions.isC2KSolution2Support()) {
                    if (isSiwtchAccountAllowed(context)) {
                        if (isSwitchFromGsmCardToCdmaCard(context, intent)) {
                            if(!setRadioCapability(context, intent)) {
                                Log.d(TAG, "Fail to set 3G/4G capability, return");
                                hideNotification(context);
                                return;
                            }
                        }
                    } else {
                        Log.d(TAG, "Not allowed to switch account in C+G case, return");
                        hideNotification(context);
                        return;
                    }
                }

                if (mNotificationShown) {
                    updateSelectedAccount(intent);
                    setAccountNotificationVisibility(true, context);
                }
                hideNotification(context);
            } else if (TelecomManagerEx.ACTION_PHONE_ACCOUNT_CHANGED.equals(action)) {
                setAccountNotificationVisibility(true, context);
            }
        }
    }

    private void hideNotification(Context context) {
        Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.sendBroadcast(intent);
    }

    private boolean isTwoCdmaCardAndInHomeNetwork(Context context) {
        int cdmaCardNum = 0;
        List<PhoneAccountHandle> accountHandles =
                PhoneAccountUtils.getSubscriptionPhoneAccounts(context);
        for (PhoneAccountHandle handle : accountHandles) {
            int subId = Integer.parseInt(handle.getId());
            int slotId = SubscriptionManager.getSlotId(subId);
            if (SvlteUiccUtils.getInstance().getSimType(slotId) == SvlteUiccUtils.SIM_TYPE_CDMA
                    && TelephonyManagerEx.getDefault().isInHomeNetwork(subId)) {
                cdmaCardNum ++;
                LogUtils.d(TAG, "this account is CDMA card and it is in home network, slotId = "
                        + slotId + ", subId = " + subId);
            }
        }
        return cdmaCardNum >= 2;
    }

    /**
     * In C + G case and 4G capability on Gsm card, it is not allowed to switch account
     * when modem reset, in call or in airplane mode.
     * @return
     */
    private boolean isSiwtchAccountAllowed(Context context) {
        int cdmaCardNum = 0;
        int gsmCardNum = 0;
        List<PhoneAccountHandle> accountHandles =
                PhoneAccountUtils.getSubscriptionPhoneAccounts(context);
        for (PhoneAccountHandle handle : accountHandles) {
            int subId = Integer.parseInt(handle.getId());
            if (SubscriptionManager.isValidSubscriptionId(subId)) {
                int slotId = SubscriptionManager.getSlotId(subId);
                if (SvlteUiccUtils.getInstance().getSimType(slotId) == SvlteUiccUtils.SIM_TYPE_CDMA
                        && TelephonyManagerEx.getDefault().isInHomeNetwork(subId)) {
                    cdmaCardNum ++;
                } else if (SvlteUiccUtils.getInstance().getSimType(slotId) ==
                        SvlteUiccUtils.SIM_TYPE_GSM) {
                    gsmCardNum ++;
                }
            }
        }
        int mainCapabilitySlotId = getMainCapabilitySlotId();
        boolean isGsmCardHasMainCapability = SvlteUiccUtils.getInstance().getSimType(
                mainCapabilitySlotId) == SvlteUiccUtils.SIM_TYPE_GSM;
        if (cdmaCardNum > 0 && gsmCardNum > 0) {
            if (isGsmCardHasMainCapability && isAirplaneModeOn(context)) {
                showAlertToast(context, R.string.can_not_switch_account_temporarily);
                return false;
            }

            ITelephonyEx iTelEx = ITelephonyEx.Stub.asInterface(
                    ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
            try {
                if (iTelEx != null && iTelEx.isCapabilitySwitching()) {
                    showAlertToast(context, R.string.can_not_switch_account_temporarily);
                    return false;
                }
            } catch (RemoteException e) {
                e.printStackTrace();
                Log.d(TAG, "fail to judge isCapabilitySwitching, RemoteException");
            }

            if (TelecomManager.from(context).isInCall()) {
                showAlertToast(context, R.string.can_not_switch_account_during_call);
                return false;
            }

            return true;
        } else {
            return true;
        }
    }

    /**
     * For C2K solution1.5, judge whether changing account to CDMA card and 3G/4G is on GSM card,
     * need check the following three items:
     * 1. has two or more SIM cards
     * 2. the selected account is CDMA card
     * 3. the 3G/4G capability is on GSM card
     * @return true if switch to CDMA card and 3G/4G capability is on GSM card.
     */
    private boolean isSwitchFromGsmCardToCdmaCard(Context context, Intent intent) {
        boolean isSeletedCdmaCard = false;
        boolean isSeletedCdmaCardInHome = false;
        boolean isGsmCardHasMainCapability = false;

        if (TelephonyManager.getDefault().getSimCount() < 2) {
            return false;
        }

        PhoneAccountHandle selectedHandle = (PhoneAccountHandle) intent.getParcelableExtra(
                EXTRA_ACCOUNT);
        if (!PhoneAccountUtils.isSubScriptionAccount(context, selectedHandle)) {
            return false;
        }

        int selectedSubId = Integer.parseInt(selectedHandle.getId());
        if (SubscriptionManager.isValidSubscriptionId(selectedSubId)) {
            int seletedSoltId = SubscriptionManager.getSlotId(selectedSubId);
            // Which slot has the Main Capability(3G/4G).
            int mainCapabilitySlotId = getMainCapabilitySlotId();
            isGsmCardHasMainCapability = SvlteUiccUtils.getInstance().getSimType(
                    mainCapabilitySlotId) == SvlteUiccUtils.SIM_TYPE_GSM;
            isSeletedCdmaCard = SvlteUiccUtils.getInstance().getSimType(seletedSoltId)
                    == SvlteUiccUtils.SIM_TYPE_CDMA;
            if (isSeletedCdmaCard) {
                isSeletedCdmaCardInHome = TelephonyManagerEx.getDefault().isInHomeNetwork(
                        selectedSubId);
            }
        }
        Log.d(TAG, "isSeletedCdmaCard = " + isSeletedCdmaCard + ", isSeletedCdmaCardInHome = "
                + isSeletedCdmaCardInHome + ", isGsmCardHasMainCapability = "
                + isGsmCardHasMainCapability);
        return isGsmCardHasMainCapability && isSeletedCdmaCard && isSeletedCdmaCardInHome;
    }

    /**
     * Set the 3G/4G capability of the SIM card
     * @param intent which has the PhoneAccountHanlde of the target SIM card
     * @return true if switch 3G/4G capability successfully
     */
    private boolean setRadioCapability(Context context, Intent intent) {
        int phoneNum = TelephonyManager.from(context).getPhoneCount();
        int[] phoneRat = new int[phoneNum];
        boolean isSwitchSuccess = true;

        PhoneAccountHandle selectedHandle = (PhoneAccountHandle) intent.getParcelableExtra(
                EXTRA_ACCOUNT);
        int subId = Integer.parseInt(selectedHandle.getId());
        int phoneId = SubscriptionManager.getPhoneId(subId);

        Log.d(TAG, "setCapability: " + phoneId);

        String curr3GSim = SystemProperties.get(PROPERTY_3G_SIM, "");
        Log.d(TAG, "current 3G Sim = " + curr3GSim);

        if (curr3GSim != null && !curr3GSim.equals("")) {
            int curr3GSlotId = Integer.parseInt(curr3GSim);
            if (curr3GSlotId == (phoneId + 1)) {
                Log.d(TAG, "Current 3G phone equals target phone, don't trigger switch");
                return isSwitchSuccess;
            }
        }

        try {
            ITelephony iTel = ITelephony.Stub.asInterface(
                    ServiceManager.getService(Context.TELEPHONY_SERVICE));
            ITelephonyEx iTelEx = ITelephonyEx.Stub.asInterface(
                    ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));

            if (null == iTel || null == iTelEx) {
                Log.e(TAG, "Can not get phone service");
                return false;
            }

            int currRat = iTel.getRadioAccessFamily(phoneId);
            Log.d(TAG, "Current phoneRat:" + currRat);

            RadioAccessFamily[] rat = new RadioAccessFamily[phoneNum];
            for (int i = 0; i < phoneNum; i++) {
                if (phoneId == i) {
                    Log.d(TAG, "SIM switch to Phone " + i);
                    if (DialerFeatureOptions.isLteSupport()) {
                        phoneRat[i] = RadioAccessFamily.RAF_LTE
                                | RadioAccessFamily.RAF_UMTS
                                | RadioAccessFamily.RAF_GSM;
                    } else {
                        phoneRat[i] = RadioAccessFamily.RAF_UMTS
                                | RadioAccessFamily.RAF_GSM;
                    }
                } else {
                    phoneRat[i] = RadioAccessFamily.RAF_GSM;
                }
                rat[i] = new RadioAccessFamily(i, phoneRat[i]);
            }
            if (false  == iTelEx.setRadioCapability(rat)) {
                Log.d(TAG, "Set phone rat fail!!!");
                isSwitchSuccess = false;
            }
        } catch (RemoteException ex) {
            Log.d(TAG, "Set phone rat fail!!!");
            ex.printStackTrace();
            isSwitchSuccess = false;
        }
        Log.d(TAG, "setRadioCapability isSwitchSuccess = " + isSwitchSuccess);
        return isSwitchSuccess;
    }

    private static int getMainCapabilitySlotId() {
        int slotId = SystemProperties.getInt(PhoneConstants.PROPERTY_CAPABILITY_SWITCH, 1) - 1;
        Log.d(TAG, "getMainCapabilitySlotId()... slotId: " + slotId);
        return slotId;
    }

    private boolean isAirplaneModeOn(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    private void showAlertToast(Context context, int resId) {
        String textErr = context.getResources().getString(resId);
        Toast.makeText(context, textErr, Toast.LENGTH_SHORT).show();
    }
}
