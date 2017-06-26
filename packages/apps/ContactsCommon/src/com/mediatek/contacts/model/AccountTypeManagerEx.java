package com.mediatek.contacts.model;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.telephony.SubscriptionInfo;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.mediatek.contacts.GlobalEnv;
import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.simcontact.SubInfoUtils;
import com.mediatek.contacts.util.AccountTypeUtils;
import com.mediatek.contacts.util.LogUtils;

import com.mediatek.contacts.model.LocalPhoneAccountType;
import com.mediatek.contacts.model.SimAccountType;
import com.mediatek.contacts.model.UimAccountType;
import com.mediatek.contacts.model.UsimAccountType;
import com.mediatek.contacts.model.AccountWithDataSetEx;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.IccCardConstants;

import java.util.List;

public class AccountTypeManagerEx {
    private static final String TAG = "AccountTypeManagerEx";

    /**
     * @param subId
     * @return the account type for this sub id
     */
    public static String getAccountTypeUsingSubId(int subId) {
        LogUtils.d(TAG, "getAccountTypeUsingSubId()+ - subId:" + subId);
        int simtype = -1;
        String simAccountType = null;

        simtype = SimCardUtils.getSimTypeBySubId(subId);
        if (SimCardUtils.SimType.SIM_TYPE_USIM == simtype) {
            simAccountType = AccountTypeUtils.ACCOUNT_TYPE_USIM;
        } else if (SimCardUtils.SimType.SIM_TYPE_SIM == simtype) {
            simAccountType = AccountTypeUtils.ACCOUNT_TYPE_SIM;
        } else if (SimCardUtils.SimType.SIM_TYPE_UIM == simtype) {
            simAccountType = AccountTypeUtils.ACCOUNT_TYPE_UIM;
        } else if (SimCardUtils.SimType.SIM_TYPE_CSIM == simtype) {
            simAccountType = AccountTypeUtils.ACCOUNT_TYPE_CSIM;
        }
        LogUtils.d(TAG, "getAccountTypeUsingSubId()- - subId:" + subId + " AccountType:"
                + simAccountType);
        return simAccountType;
    }

    /**
     * @param subId
     *            sub id
     * @return the account name for this sub id
     */
    public static String getAccountNameUsingSubId(int subId) {
        LogUtils.d(TAG, "getAccountNameUsingSubId()+ subId:" + subId);

        String accountName = null;
        String iccCardType = SimCardUtils.getIccCardType(subId);
        LogUtils.d(TAG, "getAccountNameUsingSubId() subId:" + subId
                + " iccCardType:" + iccCardType);
        if (iccCardType != null) {
            accountName = iccCardType + subId;
        }
        LogUtils.d(TAG, "getAccountNameUsingSubId()- subId:" + subId
                + " accountName:" + accountName);
        return accountName;
    }

    public static void registerReceiverOnSimStateAndInfoChanged(Context context,
            BroadcastReceiver broadcastReceiver) {
        LogUtils.d(TAG, "registerReceiverOnSimStateAndInfoChanged");
        IntentFilter simFilter = new IntentFilter();
        // For SIM Info Changed
        simFilter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        simFilter.addAction(TelephonyIntents.ACTION_PHB_STATE_CHANGED);
        simFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        context.registerReceiver(broadcastReceiver, simFilter);
    }
    
    
    public static void loadSimAndLocalAccounts(final List<AccountWithDataSet> allAccounts,
            final List<AccountWithDataSet> contactWritableAccounts,
            final List<AccountWithDataSet> groupWritableAccounts) {
        ///M: [Gemini+] add sim account @{
        List<SubscriptionInfo> subscriptionInfoList = SubInfoUtils.getActivatedSubInfoList();
        LogUtils.d(TAG, "[loadAccountsInBackground] subInfoRecords: " + subscriptionInfoList);
        if (subscriptionInfoList != null && subscriptionInfoList.size() > 0) {
            for (SubscriptionInfo subscriptionInfo : subscriptionInfoList) {
                int subId = subscriptionInfo.getSubscriptionId();
                String accountName = AccountTypeManagerEx.getAccountNameUsingSubId(subId);
                String accountType = AccountTypeManagerEx.getAccountTypeUsingSubId(subId);
                if (!TextUtils.isEmpty(accountName) && !TextUtils.isEmpty(accountType)) {
                    allAccounts.add(new AccountWithDataSetEx(accountName, accountType, subId));
                    contactWritableAccounts.add(new AccountWithDataSetEx(accountName, accountType, subId));
                    if (SimCardUtils.isSimUsimType(subId)) {
                        groupWritableAccounts.add(new AccountWithDataSetEx(accountName, accountType, subId));
                    }
                    LogUtils.d(TAG, "new AccountWithDataSetEx, AccountName: "
                            + accountName + ", AccountType: " + accountType);
                }
            }
        }
        ///@}

        // Add Phone Local Type
        /*
         * Bug Fix by Mediatek Begin.
         *   Original Android's code:
         *
         *   CR ID: ALPS00258229
         *   Descriptions: if it is tablet let accountName is tablet
         */
        if (GlobalEnv.isUsingTwoPanes()) {
            LogUtils.d(TAG, "it is tablet");
            allAccounts.add(new AccountWithDataSet("Tablet",
                    AccountTypeUtils.ACCOUNT_TYPE_LOCAL_PHONE, null));
            contactWritableAccounts.add(new AccountWithDataSet("Tablet",
                    AccountTypeUtils.ACCOUNT_TYPE_LOCAL_PHONE, null));
            groupWritableAccounts.add(new AccountWithDataSet("Tablet",
                    AccountTypeUtils.ACCOUNT_TYPE_LOCAL_PHONE, null));
        } else {
            LogUtils.d(TAG, "it is phone");
            allAccounts.add(new AccountWithDataSet("Phone",
                    AccountTypeUtils.ACCOUNT_TYPE_LOCAL_PHONE, null));
            contactWritableAccounts.add(new AccountWithDataSet("Phone",
                    AccountTypeUtils.ACCOUNT_TYPE_LOCAL_PHONE, null));
            groupWritableAccounts.add(new AccountWithDataSet("Phone",
                    AccountTypeUtils.ACCOUNT_TYPE_LOCAL_PHONE, null));
        }
        /*
         * Bug Fix by Mediatek End.
         */
    }
}
