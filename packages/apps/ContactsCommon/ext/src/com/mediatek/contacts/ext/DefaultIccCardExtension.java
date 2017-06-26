package com.mediatek.contacts.ext;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;

public class DefaultIccCardExtension implements IIccCardExtension {
    /**
     * M: provide the SimInfoRecord, to let the plug-in decide which default
     * photo should be provided to host to show on Edit view
     *
     * @param simInfo
     * @param commd
     * @return the default photo
     */
    @Override
    public Drawable getIconDrawableBySimInfoRecord(SubscriptionInfo subInfo) {
        return null;
    }

    /**
     * M: get the plug-in defined photo uri for an icc card
     *
     * @param args
     *            KEY_IS_ICC_CONTACT_SDN => Boolean: is the contact an SDN
     *            contact KEY_ICC_COLOR_ID => Integer: the color id, 0 -> blue;
     *            1 -> orange; etc
     * @param commd
     * @return The Photo uri corresponding to the args specified, plug-in should
     *         define the value. like: "content://sim-10"
     */
    @Override
    public String getIccPhotoUriString(Bundle args) {
        return null;
    }

    /**
     * M: get the plug-in defined photo id for an icc card
     *
     * @param args
     *            KEY_IS_ICC_CONTACT_SDN => Boolean: is the contact an SDN
     *            contact KEY_ICC_COLOR_ID => Integer: the color id, 0 -> blue;
     *            1 -> orange; etc
     * @param commd
     * @return The Photo id corresponding to the args specified, plug-in should
     *         define the value. like: -10 0 for default
     */
    @Override
    public long getIccPhotoId(Bundle args) {
        return 0;
    }

    /**
     * M: get the Icc Card Photo Drawable for an Icc Card
     *
     * @param args
     *            KEY_IS_DARK_THEME => Boolean whether the Host need a dark
     *            theme photo KEY_PHOTO_ID => Long specify the id of the photo
     *            host needed, can't exist along with KEY_PHOTO_URI
     *            {@link #KEY_PHOTO_URI} => Parcelable(Uri) specify the uri of
     *            the photo host needed, can't exist along with
     *            {@link #KEY_PHOTO_ID}
     * @param commd
     * @return the Drawable for the Icc Card photo, which host needs
     */
    @Override
    public Drawable getIccPhotoDrawable(Bundle args) {
        return null;
    }
}
