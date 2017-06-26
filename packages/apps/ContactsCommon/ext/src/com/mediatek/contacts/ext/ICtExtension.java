package com.mediatek.contacts.ext;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import com.mediatek.widget.CustomAccountRemoteViews.AccountInfo;

import java.util.List;

public interface ICtExtension {
    public interface StringName {
        String LOADING_SIM_CONTACTS = "msg_loading_sim_contacts_toast_ct";
        String LOSE_ACCOUNT_OR_STORAGE = "xport_error_one_account";
        String NOTIFIER_FAILURE_SIM_NOTREADY = "notifier_failure_sim_notready";
        String NOTIFIER_FAILURE_BY_SIM_FULL = "notifier_failure_by_sim_full";
    }

    /**
     * for op09
     * CT will format phone number that startWith "**133*" & endWith "#".
     * @param phoneNumber the phone to be format
     * @return phone numer been format
     */
    String formatPhoneNumber(String phoneNumber);

    /**
     * for op09 from old Api:getEnhancementPhotoId().
     *
     * @param subId get sim photo id by sub id.
     * @param sdnFlag sdn flag
     * @param photoId photo id
     * @return get the phone id for sim,-1 if no.
     */
    long getPhotoIdBySub(int subId, int sdnFlag, long photoId);

    /**
     * for op09 from old API:getEnhancementAccountSimIndicator() get the sim.
     * @param res resource
     * @param subId simid
     * @param photoDrawable photo drawable object
     * @return get the Drawable for sim
     */
    Drawable getPhotoDrawableBySub(Resources res, int subId, Drawable photoDrawable);

    /**
     * for op09 load CT sim card icon bitmap from FW.
     * @param res resource
     */
    void loadSimCardIconBitmap(Resources res);

    /**
     * for op09.
     * @param mPhotoId photo id
     * @param resultBitmap bitmap of common
     * @return the bitmap
     */
    Bitmap getOperatorIconBitmapForPhotoId(long mPhotoId, Bitmap resultBitmap);

    /**
     * for op09.
     * @param mPhotoId photo id
     * @return the result
     */
    boolean isOperatorSimPhotoId(long mPhotoId);

    /**
     * for op09.
     * @param stringName string name
     * @return get string shown in CT project
     */
    String getString(String stringName);

    /**
     * for op09.
     * @param defaultValue always icon res id
     * @return res id
     */
    int showAlwaysAskIndicate(int defaultValue);

}
