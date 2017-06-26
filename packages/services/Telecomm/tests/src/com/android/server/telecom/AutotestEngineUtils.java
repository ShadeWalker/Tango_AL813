/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
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


package com.android.server.telecom;

import java.util.List;

import junit.framework.Assert;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

/**
 * this class support AutotestEngine structure needed APIs.
 * not support other APIs.
 * utils APIs see {@link Utils}
 * call command APIs see {@link EncapsulatedInCallUIAPI}
 */
public class AutotestEngineUtils {

    private static final String TAG = "AutotestEngineUtils";

    private static final String RESULT_UNKNOWN = "UNKNOWN";
    private static final String RESULT_OK = "OK";
    private static final String RESULT_FAIL = "FAIL";
    private static final String RESULT_COMMAND_NOT_SUPPORT = "COMMAND_NOT_SUPPORT";
    private static final String RESULT_TIME_OUT = "TIME_OUT";
    private static final String RESULT_ABORT = "ABORT";

    static String resultToString(int result) {
        switch (result) {
            case ICommand.RESULT_UNKNOWN:
                return RESULT_UNKNOWN;
            case ICommand.RESULT_OK:
                return RESULT_OK;
            case ICommand.RESULT_FAIL:
                return RESULT_FAIL;
            case ICommand.RESULT_COMMAND_NOT_SUPPORT:
                return RESULT_COMMAND_NOT_SUPPORT;
            case ICommand.RESULT_TIME_OUT:
                return RESULT_TIME_OUT;
            case ICommand.RESULT_ABORT:
                return RESULT_ABORT;
            default:
                break;
        }
        return RESULT_UNKNOWN;
    }

    public static void assertAndWaitSync(int result) {
        assertAndWaitSync(result, false);
    }

    public static void assertAndWaitSync(int result, boolean checkAbortFail) {
        if (result != ICommand.RESULT_COMMAND_NOT_SUPPORT) {
            if (checkAbortFail) {
                // The case is related with the last case, so can't ignore ABORT
                // fail.
                Assert.assertTrue(result == ICommand.RESULT_OK);
            } else {
                // Not care about ABORT fail.
                Assert.assertTrue(result == ICommand.RESULT_OK || result == ICommand.RESULT_ABORT);
            }
        }
        AutotestEngine.getInstance().getInstrumentation().waitForIdleSync();
        Utils.sleep(10000);
    }

    protected static PhoneAccountHandle getAccountById(Context context, String id) {
        Utils.log(TAG, "getAccountById " + id);
        if (!TextUtils.isEmpty(id)) {
            if(id.equals("1")){
                return getFirstInServiceSubAccount(context);
            } else if(id.equals("2")) {
                return getSecondInServiceSubAccount(context);
            }
            List<PhoneAccountHandle> acountList = TelecomManager.from(context)
                    .getAllPhoneAccountHandles();
            for (PhoneAccountHandle account : acountList) {
                if (id.equals(account.getId())) {
                    return account;
                }
            }
        }
        return null;
    }

    protected static PhoneAccountHandle getDefaultAccount(Context context, String uriScheme) {
        return TelecomManager.from(context).getDefaultOutgoingPhoneAccount(uriScheme);
    }

    public static PhoneAccountHandle getFirstInServiceSubAccount(Context context) {
        PhoneFactory.makeDefaultPhones(context);
        List<PhoneAccountHandle> handles = TelecomManager.from(context)
                .getPhoneAccountsSupportingScheme(PhoneAccount.SCHEME_VOICEMAIL);
        for (PhoneAccountHandle handle : handles) {
            if(isAccountinService(handle)){
                Utils.log(TAG, "getFirstInServiceSubAccount " + handle);
                return handle;
            }
        }
        return null;
    }

    public static PhoneAccountHandle getSecondInServiceSubAccount(Context context) {
        List<PhoneAccountHandle> handles = TelecomManager.from(context)
                .getPhoneAccountsSupportingScheme(PhoneAccount.SCHEME_VOICEMAIL);
        PhoneAccountHandle firstSubId = getFirstInServiceSubAccount(context);
        for (PhoneAccountHandle handle : handles) {
            if (isAccountinService(handle) && !(handle.equals(firstSubId))) {
                Utils.log(TAG, "getSecondInServiceSubAccount " + handle);
                return handle;
            }
        }
        return null;
    }

    /**
     * Get current voice service state of special SUB account
     *
     * @see #ServiceState.STATE_IN_SERVICE
     * @see #ServiceState.STATE_OUT_OF_SERVICE
     * @see #ServiceState.STATE_EMERGENCY_ONLY
     * @see #ServiceState.STATE_POWER_OFF
     */
    public static boolean isAccountinService(PhoneAccountHandle handle) {
        int state = ServiceState.STATE_OUT_OF_SERVICE;
        if (handle.getId() != null) {
            try {
                int phoneId = SubscriptionManager.getPhoneId(Long.parseLong(handle.getId()));
                Phone phone = PhoneFactory.getPhone(phoneId);
                state = phone.getServiceState().getState();
                Utils.log(TAG, "isAccountinService handle: " + handle + " phone " + phone
                        + " state " + state);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Account is not SIM account, id can't parase to long: "
                                + handle.getId());
            }
        }
        return state != ServiceState.STATE_POWER_OFF;
    }

    /**
     * A variant of {@link #getCallIntent(android.net.Uri)} but also accept a call
     * origin and {@code Account} and {@code VideoCallProfile} state.
     * For more information about call origin, see comments in Phone package (PhoneApp).
     */
    public static Intent getCallIntent(String number, PhoneAccountHandle accountHandle) {
        final Intent intent = new Intent(Intent.ACTION_CALL, getCallUri(number));
        if (accountHandle != null) {
            intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle);
        }

        return intent;
    }
    /**
     * Return Uri with an appropriate scheme, accepting both SIP and usual phone call
     * numbers.
     */
    public static Uri getCallUri(String number) {
        if (isUriNumber(number)) {
            return Uri.fromParts(PhoneAccount.SCHEME_SIP, number, null);
        }
        return Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
    }

    public static boolean isUriNumber(String number) {
        // Note we allow either "@" or "%40" to indicate a URI, in case
        // the passed-in string is URI-escaped. (Neither "@" nor "%40"
        // will ever be found in a legal PSTN number.)
        return number != null && (number.contains("@") || number.contains("%40"));
    }

    static void log(String msg) {
        Utils.log(AutotestEngine.TAG, TAG +" " + msg);
    }
}
