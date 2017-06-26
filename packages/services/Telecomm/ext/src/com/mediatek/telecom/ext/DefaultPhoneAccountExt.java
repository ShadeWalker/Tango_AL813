package com.mediatek.telecom.ext;

import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;

import java.util.List;

public class DefaultPhoneAccountExt implements IPhoneAccountExt {

    @Override
    public void onPhoneAccountRemoved(PhoneAccountHandle accountHandle) {
        return;
    }

    @Override
    public boolean shouldResetUserSelectedOutgoingPhoneAccount(PhoneAccountHandle accountHandle,
            PhoneAccountHandle defaultAccountHandle, List<PhoneAccountHandle> accountHandleList) {
        return false;
    }

    @Override
    public PhoneAccountHandle getExPhoneAccountAsOutgoing(PhoneAccountHandle accountHandle,
            List<PhoneAccountHandle> accountHandleList) {
        return null;
    }
    
    @Override
    public boolean shouldSetDefaultOutgoingAccountAsPhoneAccount(List<PhoneAccountHandle> accountHandleList) {
        return true;
    }

}
