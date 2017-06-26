package com.mediatek.incallui.ext;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.telecom.PhoneAccount;
import android.view.View;

public interface ICallCardExt {
    /**
     * called when CallCard view created, based on CallCardFragment lifecycle
     * 
     * @param context
     *            host context
     * @param rootView
     *            the CallCardFragment view
     */
    void onViewCreated(Context context, View rootView);

    /**
     * called when call state changed, based on onStateChange
     * 
     * @param call
     *            the call who was changed
     */
    void onStateChange(android.telecom.Call call);

    /**
     * called when there is an request to update the Primary call card
     * information UI. such as: call state changed, contact info retrieved, ui
     * ready
     * 
     * @param call
     *            the primary call, Notice, call would be null
     */
    void updatePrimaryDisplayInfo(android.telecom.Call call);

    /**
     * Return the icon drawable to represent the call provider.
     * 
     * @param context
     *            for get service.
     * @param account
     *            for get icon.
     * @return The icon.
     */
    Drawable getCallProviderIcon(Context context, PhoneAccount account);

    /**
     * Return the string label to represent the call provider.
     * 
     * @param context
     *            for get service.
     * @param account
     *            for get lable.
     * @return The lable.
     */
    String getCallProviderLabel(Context context, PhoneAccount account);

    /**
     * set phone account for call.
     * 
     * @param account
     *            call via account.
     */
    void setPhoneAccountForSecondCall(PhoneAccount account);

    /**
     * set phone account for call.
     * 
     * @param account
     *            call via account.
     */
    void setPhoneAccountForThirdCall(PhoneAccount account);

    /**
     * Called when op09 plug in need to show call account icon.
     * 
     * @return true if need to show.
     */
    boolean shouldShowCallAccountIcon();

    /**
     * To get phone icon bitmap object of some call.
     * 
     * @return bitmap
     */
    Bitmap getSecondCallPhoneAccountBitmap();

    /**
     * To get phone icon bitmap object of some call.
     * 
     * @return bitmap
     */
    Bitmap getThirdCallPhoneAccountBitmap();

    /**
     * To get provider label for call.
     * 
     * @return provider label
     */
    String getSecondCallProviderLabel();

    /**
     * To get provider label for call.
     * 
     * @return provider label
     */
    String getThirdCallProviderLabel();
}
