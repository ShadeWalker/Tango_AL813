package com.mediatek.incallui.ext;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.telecom.PhoneAccount;
import android.view.View;

public class DefaultCallCardExt implements ICallCardExt {

    @Override
    public void onViewCreated(Context context, View rootView) {
        // do nothing
    }

    @Override
    public void onStateChange(android.telecom.Call call) {
        // do nothing
    }

    @Override
    public void updatePrimaryDisplayInfo(android.telecom.Call call) {
        // do nothing
    }

    /**
     * Return the icon drawable to represent the call provider.
     * 
     * @param context
     *            for get service.
     * @param account
     *            for get icon.
     * @return The icon.
     */
    @Override
    public Drawable getCallProviderIcon(Context context, PhoneAccount account) {
        return null;
    }

    /**
     * Return the string label to represent the call provider.
     * 
     * @param context
     *            for get service.
     * @param account
     *            for get lable.
     * @return The lable.
     */
    @Override
    public String getCallProviderLabel(Context context, PhoneAccount account) {
        return null;
    }

    @Override
    public void setPhoneAccountForSecondCall(PhoneAccount account) {
        // do nothing
    }

    @Override
    public void setPhoneAccountForThirdCall(PhoneAccount account) {
        // do nothing
    }

    @Override
    public boolean shouldShowCallAccountIcon() {
        return false;
    }

    @Override
    public Bitmap getSecondCallPhoneAccountBitmap() {
        return null;
    }

    @Override
    public Bitmap getThirdCallPhoneAccountBitmap() {
        return null;
    }

    @Override
    public String getSecondCallProviderLabel() {
        return null;
    }

    @Override
    public String getThirdCallProviderLabel() {
        return null;
    }

}
