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

/*
 *
 */

package com.mediatek.internal.telephony.ltedc;

import android.app.ActivityManagerNative;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;

import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.Phone.DataActivityState;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.RadioCapability;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.dataconnection.DcFailCause;
import com.android.internal.telephony.dataconnection.DcTracker;

import com.mediatek.internal.telephony.ltedc.svlte.SvlteModeController;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteRatController;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteRilArbitrator;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;
import com.mediatek.internal.telephony.ltedc.svlte.apirat.SvlteIrController;

/**
 * For SGLTE/SVLTE, manage CS/PS phone and a RIL Request/URC arbitrator
 */
public class LteDcPhoneProxy extends PhoneProxy {
    private static final String LOG_TAG = "PHONE";

    protected Context mContext;
    protected IRilDcArbitrator mRilDcArbitrator;

    protected PhoneBase mLtePhone;
    protected PhoneBase mNLtePhone;
    protected PhoneBase mPsPhone;
    protected PhoneBase mCsPhone;

    protected DcTracker mSharedDcTracker;
    protected SvlteRatController mSvlteRatController;
    protected SvlteIrController mSvlteIrController;

    private static final Uri PREFERAPN_NO_UPDATE_URI_USING_SUBID =
            Uri.parse("content://telephony/carriers/preferapn_no_update/subId/");
    private static final String APN_ID = "apn_id";

    /**
     * Public constructor, pass two phone, one for LTE, one for GSM or CDMA.
     * @param ltePhone The LTE Phone
     * @param nltePhone The non LTE Phone
     * @param radioTechMode Current radio technology mode
     */
    public LteDcPhoneProxy(PhoneBase ltePhone, PhoneBase nltePhone,  int radioTechMode) {
        super(radioTechMode == SvlteModeController.RADIO_TECH_MODE_SVLTE ? nltePhone : ltePhone);
        mLtePhone = ltePhone;
        mNLtePhone = nltePhone;
        mContext = mNLtePhone.getContext();

        mCsPhone = getDefaultCsPhone();
        mPsPhone = getDefaultPsPhone();
        mRilDcArbitrator = new SvlteRilArbitrator(mLtePhone, mNLtePhone);
        mSvlteRatController = new SvlteRatController(this);
        mSvlteIrController = new SvlteIrController(this);
        logd("LteDcPhoneProxy: mLtePhone = " + mLtePhone + ", mNLtePhone = "
                + mNLtePhone);
    }

    @Override
    public void dispose() {
        if (mLtePhone != null) {
            mLtePhone.dispose();
        }
        if (mNLtePhone != null) {
            mNLtePhone.dispose();
        }
    }

    @Override
    public void removeReferences() {
        logd("removeReferences: mLtePhone = " + mLtePhone + ", mNLtePhone = "
                + mNLtePhone);
        if (mLtePhone != null) {
            mLtePhone.removeReferences();
        }
        if (mNLtePhone != null) {
            mNLtePhone.removeReferences();
        }
    }

    /**
     * Initialize params and components, avoid cycle reference in
     * PhoneFactory.getPhone().
     */
    public void initialize() {

    }

    /**
     * Get the PS Phone.
     * @return The PS Phone
     */
    public PhoneBase getPsPhone() {
        return mPsPhone;
    }

    /**
     * Get the CS Phone.
     * @return The CS Phone
     */
    public Phone getCsPhone() {
        return mCsPhone;
    }

    /**
     * Set the PS Phone.
     * @param psPhone The PS Phone to set
     */
    public void setPsPhone(PhoneBase psPhone) {
        mPsPhone = psPhone;
    }

    /**
     * Set the CS Phone.
     * @param csPhone The CS Phone to set
     */
    public void setCsPhone(PhoneBase csPhone) {
        mCsPhone = csPhone;
    }

    /**
     * Get the PS Phone.
     * @return The PS Phone
     */
    public PhoneBase getLtePhone() {
        return mLtePhone;
    }

    /**
     * Get the PS Phone.
     * @return The PS Phone
     */
    public PhoneBase getNLtePhone() {
        return mNLtePhone;
    }

    /**
     * Set the LTE Phone.
     * @param ltePhone The LTE Phone to set
     */
    public void setLtePhone(PhoneBase ltePhone) {
        mLtePhone = ltePhone;
    }

    /**
     * Set the non LTE Phone.
     * @param nltePhone The non LTE Phone to set
     */
    public void setNLtePhone(PhoneBase nltePhone) {
        mNLtePhone = nltePhone;
    }

    protected PhoneBase getDefaultCsPhone() {
        return mNLtePhone;
    }

    protected PhoneBase getDefaultPsPhone() {
        return mLtePhone;
    }

    /**
     * Update PS phone when data rat changed.
     * @param sourceRat source data rat
     * @param targetRat target data rat
     */
    public void updatePsPhone(int sourceRat, int targetRat) {
        log("updatePsPhone, sourceRat=" + sourceRat + ", targetRat="
                + targetRat);

        switch (targetRat) {
            case ServiceState.RIL_RADIO_TECHNOLOGY_LTE:
                log("updatePsPhone to ltePhone");
                mPsPhone = mLtePhone;
                break;

            case ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD:
            case ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_B:
            case ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A:
            case ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_0:
            case ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT:
            case ServiceState.RIL_RADIO_TECHNOLOGY_IS95A:
            case ServiceState.RIL_RADIO_TECHNOLOGY_IS95B:
                log("updatePsPhone to nltePhone");
                mPsPhone = mNLtePhone;
                break;

            default:
                log("updatePsPhone, target rat is unknown, keep on.");
                return;
        }

        // update ps Ril
        updatePsPhoneAndCi(mPsPhone);
    }

    /**
     * Update PS phone and RIL in IRAT project.
     * @param psPhone PS phone.
     */
    public void updatePsPhoneAndCi(PhoneBase psPhone) {
        log("updatePsPhoneAndCi: psPhone = " + psPhone);
        mPsPhone = psPhone;
        mRilDcArbitrator.updatePsCi(((PhoneBase) mPsPhone).mCi);
    }

    /**
     * Get shared DcTracker.
     * @return Shared DcTracker.
     */
    public DcTracker getSharedDcTracker() {
        return mSharedDcTracker;
    }

    /**
     * Set Radio power for SVLTE LTEDcPhone.
     * @param power desired radio power
     * @param phoneId The id of the phone to set
     */
    public void setRadioPower(boolean power, int phoneId) {
        log("setRadioPower phoneId=" + phoneId + " power=" + power);
        if (getPhoneById(phoneId) != null) {
            getPhoneById(phoneId).setRadioPower(power);
        }
    }

    /**
     * Get Phone using phone id.
     * @param phoneId The id to acces Phone.
     * @return The specified phone.
     */
    public Phone getPhoneById(int phoneId) {
        if (phoneId == mNLtePhone.getPhoneId()) {
            return mNLtePhone;
        } else if (phoneId == mLtePhone.getPhoneId()) {
            return mLtePhone;
        } else {
            log("getPhoneById should come here");
            return null;
        }
    }

    /**
     * Switch active phone.
     * @param radioTech RadioTech which radio technology mode want to change.
     * SvlteModeController.RADIO_TECH_MODE_CSFB: change active phone to SvlteDcPhone
     * SvlteModeController.RADIO_TECH_MODE_SVLTE: change active phone to CDMAPhone
     */
    public void toggleActivePhone(int radioTech) {
        final Phone activePhone = getActivePhone();
        boolean lteMode = false;
        if (radioTech == SvlteModeController.RADIO_TECH_MODE_CSFB) {
            logd("toggleActivePhone to CSFB mode");
            lteMode = true;
        } else if (radioTech == SvlteModeController.RADIO_TECH_MODE_SVLTE) {
            logd("toggleActivePhone to SVLTE mode");
            lteMode = false;
        } else {
            logd("toggleActivePhone return, Unknown mode");
            return;
        }
        if (activePhone == null || (lteMode && activePhone.equals(mLtePhone))
                || (!lteMode && activePhone.equals(mNLtePhone))) {
            log("switchActivePhone return without action, lteMode = " + lteMode
                    + ", activePhone = " + activePhone + ", mLtePhone = "
                    + mLtePhone + ", mNLtePhone = " + mNLtePhone);
            return;
        }
        switchActivePhone(lteMode ? (PhoneBase) mLtePhone
                : (PhoneBase) mNLtePhone);
    }

    protected void switchActivePhone(Phone targetPhone) {
        logd("switchActivePhone targetPhone=" + targetPhone + ", oldPhone="
                + mActivePhone);
        Phone oldPhone = mActivePhone;
        mActivePhone = targetPhone;

        // Update ActivePhone for CallManager
        CallManager.getInstance().registerPhone(mActivePhone);
        CallManager.getInstance().unregisterPhone(oldPhone);

        updatePhoneIds(oldPhone, mActivePhone);

        // Set the new interfaces in the proxy's
        mPhoneSubInfoProxy.setmPhoneSubInfo(mActivePhone.getPhoneSubInfo());

        mCommandsInterface = ((PhoneBase) mActivePhone).mCi;
        mIccCardProxy
                .setVoiceRadioTech(mActivePhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM ? ServiceState.RIL_RADIO_TECHNOLOGY_GSM
                        : ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A);
        //Update card files
        mIccCardProxy.updateIccRefresh();

        // Update PS Phone
        int oldSs = mActivePhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM ? ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A
                : ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
        int newSs = mActivePhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM ? ServiceState.RIL_RADIO_TECHNOLOGY_LTE
                : ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A;
        updatePsPhone(oldSs, newSs);

        // Send an Intent to the PhoneApp that we had a radio technology change
        Intent intent = new Intent(
                TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(PhoneConstants.PHONE_NAME_KEY,
                mActivePhone.getPhoneName());
        putIdsExtra(intent, getPhoneId(), oldPhone.getPhoneId());
        ActivityManagerNative.broadcastStickyIntent(intent, null,
                UserHandle.USER_ALL);
    }

    private void updatePhoneIds(Phone oldPhone, Phone newPhone) {
        // Update phone id, just switch between old and new
        int oldPhoneId = oldPhone.getPhoneId();
        int newPhoneId = mActivePhone.getPhoneId();
        oldPhone.setPhoneId(newPhoneId);
        mActivePhone.setPhoneId(oldPhoneId);

        updatePreferApnForSubIdChanged(mActivePhone, oldPhone);
    }

    @Override
    public void getDataCallList(Message response) {
        mPsPhone.getDataCallList(response);
    }

    @Override
    public boolean getDataRoamingEnabled() {
        return mPsPhone.getDataRoamingEnabled();
    }

    @Override
    public void setDataRoamingEnabled(boolean enable) {
        mPsPhone.setDataRoamingEnabled(enable);
    }

    @Override
    public boolean getDataEnabled() {
        return mPsPhone.getDataEnabled();
    }

    @Override
    public void setDataEnabled(boolean enable) {
        mPsPhone.setDataEnabled(enable);
    }

    @Override
    public boolean isDataConnectivityPossible() {
        return mPsPhone
                .isDataConnectivityPossible(PhoneConstants.APN_TYPE_DEFAULT);
    }

    @Override
    public boolean isDataConnectivityPossible(String apnType) {
        return mPsPhone.isDataConnectivityPossible(apnType);
    }

    public DcFailCause getLastDataConnectionFailCause(String apnType) {
        return mPsPhone.getLastDataConnectionFailCause(apnType);
    }

    @Override
    public PhoneConstants.DataState getDataConnectionState() {
        return mPsPhone.getDataConnectionState(PhoneConstants.APN_TYPE_DEFAULT);
    }

    @Override
    public PhoneConstants.DataState getDataConnectionState(String apnType) {
        return mPsPhone.getDataConnectionState(apnType);
    }

    @Override
    public DataActivityState getDataActivityState() {
        return mPsPhone.getDataActivityState();
    }

    /**
     * Return the RilArbitrator.
     * @return IRilDcArbitrator
     */
    public IRilDcArbitrator getRilDcArbitrator() {
        return mRilDcArbitrator;
    }

    /**
     * Return the SvlteRatController.
     * @return SvlteRatController
     */
    public SvlteRatController getSvlteRatController() {
        return mSvlteRatController;
    }

    /**
     * Get svlte phony proxy CallTracker.
     * @return cs phone CallTracker
     */
    public CallTracker getCallTracker() {
        return mCsPhone.getCallTracker();
    }

    /**
     * To override log format, add LteDcPhoneProxy prefix.
     * @param msg The log to print
     */
    public void log(String msg) {
        Rlog.i(LOG_TAG, "[" + getLogTag() + "] " + msg);
    }

    /**
     * To override log format, add LteDcPhoneProxy prefix.
     * @param msg The log to print
     */
    public void logv(String msg) {
        Rlog.v(LOG_TAG, "[" + getLogTag() + "] " + msg);
    }

    /**
     * To override log format, add LteDcPhoneProxy prefix.
     * @param msg The log to print
     */
    public void logd(String msg) {
        Rlog.d(LOG_TAG, "[" + getLogTag() + "] " + msg);
    }

    /**
     * To override log format, add LteDcPhoneProxy prefix.
     * @param msg The log to print
     */
    public void logw(String msg) {
        Rlog.w(LOG_TAG, "[" + getLogTag() + "] " + msg);
    }

    /**
     * To override log format, add LteDcPhoneProxy prefix.
     * @param msg The log to print
     */
    public void loge(String msg) {
        Rlog.e(LOG_TAG, "[" + getLogTag() + "] " + msg);
    }

    /**
     * To override log format, add prefix.
     * @return The prefix
     */
    protected String getLogTag() {
        return "IRAT_LteDcPhoneProxy";
    }

    private void updatePreferApnForSubIdChanged(Phone activePhone, Phone oldPhone) {
        int activeSubId = activePhone.getSubId();
        int oldSubId = oldPhone.getSubId();
        int activeApnId = getPreferredApnIdForSub(activeSubId);
        int oldApnId = getPreferredApnIdForSub(oldSubId);
        log("updatePreferApnForSubIdChanged: activeSubId = " + activeSubId + ",oldSubId = " +
                oldSubId + ",activeApnId = " + activeApnId + ",oldApnId = " + oldApnId);
        setpreferredApnForSub(activeSubId, oldApnId);
        setpreferredApnForSub(oldSubId, activeApnId);
    }

    private int getPreferredApnIdForSub(int subId) {
        int apnId = -1;
        String subIdString = Integer.toString(subId);
        Uri uri = Uri.withAppendedPath(PREFERAPN_NO_UPDATE_URI_USING_SUBID, subIdString);
        Cursor cursor = mContext.getContentResolver().query(
                uri, new String[] { "_id", "name", "apn" },
                null, null, Telephony.Carriers.DEFAULT_SORT_ORDER);
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            apnId = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers._ID));
        }
        if (cursor != null) {
            cursor.close();
        }
        log("getPreferredApnIdForSub: subId = " + subId + ",apnId = " + apnId);
        return apnId;
    }

    private void setpreferredApnForSub(int subId, int apnId) {
        log("setpreferredApnForSub: subId = " + subId + ",apnId = " + apnId);
        String subIdString = Integer.toString(subId);
        Uri uri = Uri.withAppendedPath(PREFERAPN_NO_UPDATE_URI_USING_SUBID, subIdString);
        ContentResolver resolver = mContext.getContentResolver();
        resolver.delete(uri, null, null);

        if (apnId >= 0) {
            ContentValues values = new ContentValues();
            values.put(APN_ID, apnId);
            resolver.insert(uri, values);
        }
    }

    @Override
    public void setRadioCapability(RadioCapability rc, Message response) {
        if (rc.getPhase() == RadioCapability.RC_PHASE_START) {
            if (rc.getPhoneId() == SvlteModeController.getCdmaSocketSlotId()) {
                // send request to MD3 to power off modem
                mNLtePhone.setRadioCapability(rc, response);
                return;
            }
        }
        mLtePhone.setRadioCapability(rc, response);

    }

    @Override
    public int getRadioAccessFamily() {
        return mLtePhone.getRadioAccessFamily();
    }

    @Override
    public void registerForRadioCapabilityChanged(Handler h, int what, Object obj) {
        mLtePhone.registerForRadioCapabilityChanged(h, what, obj);
    }

    @Override
    public void unregisterForRadioCapabilityChanged(Handler h) {
        mLtePhone.unregisterForRadioCapabilityChanged(h);
    }

    private void putIdsExtra(Intent intent, int newPhoneId, int oldPhoneId) {
        int[] phoneIds = {newPhoneId, oldPhoneId};
        for (int i = 0; i < phoneIds.length; i++) {
            int phoneId = phoneIds[i];
            int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            int slotId = SvlteUtils.getSlotId(phoneId);
            if (SvlteUtils.isLteDcPhoneId(phoneId)) {
                subId = SvlteUtils.getLteDcSubId(slotId);
            } else {
                int[] subIds = SubscriptionManager.getSubId(slotId);
                if (subIds != null && subIds.length > 0) {
                    subId = subIds[0];
                }
            }
            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                if (i == 0) {
                   putNewIdsExtra(intent, phoneId, slotId, subId);
                } else {
                   putOldIdsExtra(intent, phoneId, slotId, subId);
                }
            } else {
                logd("putIdsExtra: no valid sub id found for "
                        + (i == 0 ? "newphone" : "oldphone"));
            }
        }
    }

    private void putNewIdsExtra(Intent intent, int phoneId, int slotId, int subId) {
        log("putNewIdsExtra: phoneId=" + phoneId + " slotId=" + slotId + " subId=" + subId);
        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);
        intent.putExtra(PhoneConstants.PHONE_KEY, phoneId);
        intent.putExtra(PhoneConstants.SLOT_KEY, slotId);
    }

    private void putOldIdsExtra(Intent intent, int phoneId, int slotId, int subId) {
        log("putOldIdsExtra: phoneId=" + phoneId + " slotId=" + slotId + " subId=" + subId);
        intent.putExtra(PhoneConstants.OLD_SUBSCRIPTION_KEY, subId);
        intent.putExtra(PhoneConstants.OLD_PHONE_KEY, phoneId);
        intent.putExtra(PhoneConstants.OLD_SLOT_KEY, slotId);
    }
}
