package com.mediatek.telecom.ext;

import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;

import java.util.List;

public interface IPhoneAccountExt {

    /**
     * Need to notify Plug in some phone account is removed.
     * 
     * @param accountHandle
     *            some acccount that is removed.
     */
    void onPhoneAccountRemoved(PhoneAccountHandle accountHandle);

    /**
     * should reset user selected phone account as outgoing phone account if
     * necessary.
     * 
     * @param accountHandle
     *            user selected phone account.
     * @param defaultAccountHandle
     *            user selected phone account.
     * @param accountHandleList
     *            a list of all Phone accounts.
     * @return true if need to reset outgoing phone account.
     */
    boolean shouldResetUserSelectedOutgoingPhoneAccount(PhoneAccountHandle accountHandle,
            PhoneAccountHandle defaultAccountHandle, List<PhoneAccountHandle> accountHandleList);

    /**
     * Whether or not to set User selected Phone account as outgoing by plug-in
     * Rule.
     * 
     * @param accountHandle
     *            default outgoing phone account.
     * @param accountHandleList
     *            phone account list available on device.
     * @return a account that plug in select.
     */
    PhoneAccountHandle getExPhoneAccountAsOutgoing(PhoneAccountHandle accountHandle,
            List<PhoneAccountHandle> accountHandleList);

    /**
     * To set out going call account by account list size.
     * 
     * @param accountHandleList
     *            capable account list
     * @return true if need to set.
     */
    boolean shouldSetDefaultOutgoingAccountAsPhoneAccount(List<PhoneAccountHandle> accountHandleList);
}
