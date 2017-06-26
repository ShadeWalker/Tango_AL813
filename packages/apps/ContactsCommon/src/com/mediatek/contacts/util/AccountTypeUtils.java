/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.mediatek.contacts.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.provider.ContactsContract.CommonDataKinds.ImsCall;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.util.Log;
import android.util.SparseArray;

import com.android.contacts.common.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType.EditField;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.account.BaseAccountType.SimpleInflater;
import com.android.contacts.common.model.dataitem.DataKind;

import com.google.common.collect.Lists;

import com.mediatek.contacts.ExtensionManager;
import com.mediatek.contacts.model.AccountWithDataSetEx;
import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.simcontact.SlotUtils;
import com.mediatek.contacts.simcontact.SubInfoUtils;

import java.util.List;
import java.util.Locale;

/**
 * some methods related to AccountType.
 */
public class AccountTypeUtils {

    private static final String TAG = "AccountTypeUtils";

    public static final String ACCOUNT_TYPE_SIM = "SIM Account";
    public static final String ACCOUNT_TYPE_USIM = "USIM Account";
    public static final String ACCOUNT_TYPE_LOCAL_PHONE = "com.android.huawei.phone";
    public static final String ACCOUNT_TYPE_UIM = "UIM Account";
    public static final String ACCOUNT_TYPE_CSIM = "CSIM Account";
    public static final String ACCOUNT_NAME_LOCAL_PHONE = "Phone";
    public static final String ACCOUNT_NAME_LOCAL_TABLET = "Tablet";

    public static final SparseArray<SparseArray<String>> SIM_ACCOUNT_NAME_ARRAY =
            new SparseArray<SparseArray<String>>(SlotUtils.getSlotCount());

    static {
        for (int slotId : SlotUtils.getAllSlotIds()) {
            SparseArray<String> accountNamesForSlot = new SparseArray<String>();
            accountNamesForSlot.put(SimCardUtils.SimType.SIM_TYPE_SIM, "SIM" + slotId);
            accountNamesForSlot.put(SimCardUtils.SimType.SIM_TYPE_USIM, "USIM" + slotId);
            accountNamesForSlot.put(SimCardUtils.SimType.SIM_TYPE_UIM, "UIM" + slotId);
            accountNamesForSlot.put(SimCardUtils.SimType.SIM_TYPE_CSIM, "CSIM" + slotId);
            SIM_ACCOUNT_NAME_ARRAY.put(slotId, accountNamesForSlot);
        }
    }


    /*
     * Descriptions: cu feature change photo by slot id
     * @param context the Context.
     * @param subId the current sub id.
     * @return  the icon of the sub id.
     */
    public static Drawable getDisplayIconBySubId(Context context, int subId,
            int titleRes, int iconRes, String syncAdapterPackageName) {

        if (titleRes != -1 && syncAdapterPackageName != null) {
            LogUtils.d("checkphoto", "[Accounttype] summaryrespackagename !=null");
            final PackageManager pm = context.getPackageManager();
            return pm.getDrawable(syncAdapterPackageName, iconRes, null);
        } else if (titleRes != -1) {
            /*int photo = iconRes;
            LogUtils.d(TAG, "[Accounttype] subId = " + subId);
            int i = SubInfoUtils.getColorUsingSubId(subId);
            LogUtils.d(TAG, "[Accounttype] i = " + i);
            if (i == 0) {
                photo = R.drawable.sim_indicator_yellow;
            } else if (i == 1) {
                photo = R.drawable.sim_indicator_orange;
            } else if (i == 2) {
                photo = R.drawable.sim_indicator_green;
            } else if (i == 3) {
                photo = R.drawable.sim_indicator_purple;
            } else {
                photo = R.drawable.mtk_ic_contact_account_sim;
            }
            return context.getResources().getDrawable(photo);*/
            // Using google default sim icon.
            Drawable photoDrable = SubInfoUtils.getIconDrawable(subId);
            return ExtensionManager
                    .getInstance()
                    .getCtExtension()
                    .getPhotoDrawableBySub(context.getResources(), subId, photoDrable);
        } else {
            return null;
        }
    }

    /**
     * If the account name is one of Icc Card account names, like
     * "USIM Account" return true, otherwise, means the account is not a
     * SIM/USIM/UIM account.
     *
     * FIXME: this implementation is not good, not OO. should try to remove it
     * in future refactor
     *
     * @param accountTypeString
     *            generally, it's a string like "USIM Account" or
     *            "Local Phone Account"
     * @return if it's a IccCard account, return true, otherwise false.
     */
    public static boolean isAccountTypeIccCard(String accountTypeString) {
        boolean isIccCardAccount = (ACCOUNT_TYPE_SIM.equals(accountTypeString)
                || ACCOUNT_TYPE_USIM.equals(accountTypeString)
                || ACCOUNT_TYPE_UIM.equals(accountTypeString)
                || ACCOUNT_TYPE_CSIM.equals(accountTypeString));
        LogUtils.d(TAG, "account " + accountTypeString + " is IccCard? " + isIccCardAccount);
        return isIccCardAccount;
    }

    /**
     * Considering SIM account will appear as it's display name, so
     * we wrapper this method to get display account name.
     *
     * @param context Context
     * @param accountName
     *            the original name
     * @return if the account is a SIM account, then return it's display name,
     *         otherwise, do nothing
     */
    public static String getDisplayAccountName(Context context, String accountName) {
        int subId = getSubIdBySimAccountName(context, accountName);
        return subId < 1 ? accountName : SubInfoUtils.getDisplaynameUsingSubId(subId);
    }

    /**
     * Get the subId based on account name, return -1 if the account is not
     * SIM account.
     * @param context Context
     * @param accountName String
     */
    public static int getSubIdBySimAccountName(Context context, String accountName) {
        List<AccountWithDataSet> accounts = AccountTypeManager.getInstance(context)
                .getAccounts(true);
        for (AccountWithDataSet account : accounts) {
            if (account instanceof AccountWithDataSetEx && account.name.equals(accountName)) {
                return ((AccountWithDataSetEx) account).getSubId();
            }
        }
        if (Build.TYPE.equals("eng")) {
            Log.d(TAG, "account " + accountName + " is not sim account");
        }
        return SubInfoUtils.getInvalidSubId();
    }

    /**
     * CMCC requests 7 edit fields in StructuredPostal.
     * @param kind the data kind of StructuredPostal.
     * @param postalFlags BaseAccountType.FLAGS_POSTAL.
     */
    public static void setStructuredPostalFiledList(DataKind kind, int postalFlags) {
        final boolean useJapaneseOrder = Locale.JAPANESE.getLanguage().equals(
                Locale.getDefault().getLanguage());
        if (useJapaneseOrder) {
            kind.fieldList.add(new EditField(StructuredPostal.COUNTRY, R.string.postal_country,
                    postalFlags).setOptional(true));
            kind.fieldList.add(new EditField(StructuredPostal.POSTCODE, R.string.postal_postcode,
                    postalFlags));
            kind.fieldList.add(new EditField(StructuredPostal.REGION, R.string.postal_region,
                    postalFlags));
            kind.fieldList.add(new EditField(StructuredPostal.CITY, R.string.postal_city,
                    postalFlags));
            kind.fieldList.add(new EditField(StructuredPostal.NEIGHBORHOOD,
                    R.string.postal_neighborhood, postalFlags));
            kind.fieldList.add(new EditField(StructuredPostal.POBOX, R.string.postal_pobox,
                    postalFlags));
            kind.fieldList.add(new EditField(StructuredPostal.STREET, R.string.postal_street,
                    postalFlags));
        } else {
            kind.fieldList.add(new EditField(StructuredPostal.STREET, R.string.postal_street,
                    postalFlags));
            kind.fieldList.add(new EditField(StructuredPostal.POBOX, R.string.postal_pobox,
                    postalFlags));
            kind.fieldList.add(new EditField(StructuredPostal.NEIGHBORHOOD,
                    R.string.postal_neighborhood, postalFlags));
            kind.fieldList.add(new EditField(StructuredPostal.CITY, R.string.postal_city,
                    postalFlags));
            kind.fieldList.add(new EditField(StructuredPostal.REGION, R.string.postal_region,
                    postalFlags));
            kind.fieldList.add(new EditField(StructuredPostal.POSTCODE, R.string.postal_postcode,
                    postalFlags));
            kind.fieldList.add(new EditField(StructuredPostal.COUNTRY, R.string.postal_country,
                    postalFlags).setOptional(true));
        }
    }

    /**
     * VOLTE IMS Call feature, set the content of IMS call data kind.
     * @param kind the data kind of ImsCall.
     * @param flagsSipAddress BaseAccountType.FLAGS_SIP_ADDRESS.
     */
    public static void setDataKindImsCall(DataKind kind, int flagsSipAddress) {
        kind.actionHeader = new SimpleInflater(R.string.imsCallLabelsGroup);
        kind.actionBody = new SimpleInflater(ImsCall.URL);
        kind.typeOverallMax = 1;

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(ImsCall.URL, R.string.imsCallLabelsGroup,
                flagsSipAddress));
    }
}
