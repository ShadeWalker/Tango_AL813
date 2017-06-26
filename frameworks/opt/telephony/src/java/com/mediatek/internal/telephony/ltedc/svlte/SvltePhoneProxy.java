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

package com.mediatek.internal.telephony.ltedc.svlte;

import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.telephony.ServiceState;

import com.android.internal.telephony.IccPhoneBookInterfaceManagerProxy;
import com.android.internal.telephony.IccSmsInterfaceManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.dataconnection.DcTracker;

import com.mediatek.common.MPlugin;
import com.mediatek.common.telephony.ITelephonyExt;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.ltedc.LteDcConstants;
import com.mediatek.internal.telephony.ltedc.LteDcPhoneProxy;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteRatController.RoamingMode;
import com.mediatek.internal.telephony.uicc.SvlteUiccUtils;

/**
 * For SVLTE, manage CS/PS phone and a RIL Request/URC arbitrator
 */
public class SvltePhoneProxy extends LteDcPhoneProxy {

    private IratController mIratController;
    private IratDataSwitchHelper mIratDataSwitchHelper;

    protected IccSmsInterfaceManager mCdmaIccSmsInterfaceManager;
    protected IccSmsInterfaceManager mLteIccSmsInterfaceManager;
    protected IccPhoneBookInterfaceManagerProxy mCdmaIccPhoneBookInterfaceManagerProxy;
    protected IccPhoneBookInterfaceManagerProxy mLteIccPhoneBookInterfaceManagerProxy;

    ITelephonyExt mTelephonyExt;

    /**
     * Public constructor, pass two phone, one for LTE, one for GSM or CDMA.
     * @param gsmPhone The LTE Phone
     * @param cdmaPhone The non LTE Phone
     * @param radioTechMode Current radio technology mode
     */
    public SvltePhoneProxy(PhoneBase gsmPhone, PhoneBase cdmaPhone, int radioTechMode) {
        super(gsmPhone, cdmaPhone, radioTechMode);
        logd("SvltePhoneProxy: cdmaPhone = " + cdmaPhone + ", gsmPhone = "
                + gsmPhone);

        if (radioTechMode == SvlteModeController.RADIO_TECH_MODE_SVLTE) {
            if (mIccSmsInterfaceManager != null) {
                mCdmaIccSmsInterfaceManager = mIccSmsInterfaceManager;
            } else {
                mCdmaIccSmsInterfaceManager = new IccSmsInterfaceManager(cdmaPhone);
            }
                mLteIccSmsInterfaceManager = new IccSmsInterfaceManager(gsmPhone);
        } else {
            mCdmaIccSmsInterfaceManager = new IccSmsInterfaceManager(cdmaPhone);
            if (mIccSmsInterfaceManager != null) {
                mLteIccSmsInterfaceManager = mIccSmsInterfaceManager;
            } else {
                mLteIccSmsInterfaceManager = new IccSmsInterfaceManager(gsmPhone);
            }
        }
        updateIccSmsInterfaceManager(getActivePhone());

        mCdmaIccPhoneBookInterfaceManagerProxy = new IccPhoneBookInterfaceManagerProxy(
                cdmaPhone.getIccPhoneBookInterfaceManager());
        mLteIccPhoneBookInterfaceManagerProxy = new IccPhoneBookInterfaceManagerProxy(
                gsmPhone.getIccPhoneBookInterfaceManager());
        updateIccPhoneBookInterfaceManager(getActivePhone());

        setCsPhone(getDefaultCsPhone());
        setPsPhone(getDefaultPsPhone());

        SvlteSstProxy.make(this);

        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            mIratController = new MdIratController(this);
            mIratDataSwitchHelper = new MdIratDataSwitchHelper(this);
        }
        logd("CdmaLteDcPhoneProxy: mIratController = " + mIratController
                + ", mIratDataSwitchHelper = " + mIratDataSwitchHelper);
        shareLTEServiceStateTracker(gsmPhone, cdmaPhone);

        mTelephonyExt = MPlugin.createInstance(ITelephonyExt.class.getName(),
                mContext);
         if (mTelephonyExt == null) {
             log("Get ITelephonyExt fail");
         } else {
             mTelephonyExt.init(mContext);
         }
         /// M: For CT IR data customization
         if (mTelephonyExt != null) {
             mTelephonyExt.startDataRoamingStrategy(this);
         }

    }

    @Override
    protected PhoneBase getDefaultCsPhone() {
        return (PhoneBase) mActivePhone;
    }

    @Override
    protected PhoneBase getDefaultPsPhone() {
        SvlteRatController.SvlteRatMode ratMode = getRatMode();
        logd("getDefaultPsPhone: ratMode =" + ratMode);
        if (!ratMode.isCdmaOn()) {
            return mLtePhone;
        } else {
            return (PhoneBase) mActivePhone;
        }
    }

    private void createAndShareDcTracker() {
        mSharedDcTracker = new DcTracker(mPsPhone);
        logd("createAndShareDcTracker: mSharedDcTracker =" + mSharedDcTracker);
        mLtePhone.mDcTracker = mSharedDcTracker;
        mNLtePhone.mDcTracker = mSharedDcTracker;
    }

    /**
     * Get SVLTE RAT mode.
     * @return SVLTE RAT mode.
     */
    public SvlteRatController.SvlteRatMode getRatMode() {
        ///get subId
        int subId = getSubId();
        int ratMode = Settings.Global.getInt(mContext.getContentResolver(),
                SvlteUtils.getCdmaRatModeKey(subId),
                SvlteRatController.SvlteRatMode.SVLTE_RAT_MODE_4G.ordinal());
        logd("getRatMode ratMode= " + ratMode + " subId = " + subId);
        return SvlteRatController.SvlteRatMode.values()[ratMode];
    }

    private void shareLTEServiceStateTracker(PhoneBase gsmPhone, PhoneBase cdmaPhone) {
        logd("shareLTEServiceStateTracker: cdmaPhone=" + cdmaPhone
                + ", gsmPhone=" + gsmPhone);
        SvlteServiceStateTracker lteServiceStateTracker = (SvlteServiceStateTracker) cdmaPhone
                .getServiceStateTracker();
        gsmPhone.getServiceStateTracker().setSvlteServiceStateTracker(
                lteServiceStateTracker);
    }

    @Override
    public void dispose() {
        super.dispose();
        logd("dispose: mSharedDcTracker =" + mSharedDcTracker);

        mSharedDcTracker.dispose();
        mIratDataSwitchHelper.dispose();
        if (mTelephonyExt != null) {
            mTelephonyExt.stopDataRoamingStrategy();
        }
    }

    @Override
    public String getLogTag() {
        return "SvltePhoneProxy";
    }

    /**
     * Do not need phone update in SVLTE case.LTEDcPhone's RAT change to LTE after
     * LTE camp successful, and it will trigger phone object update.
     * But it uses active phone CDMAPhone to update phone object, which causes a C->G phone update.
     * @param voiceRadioTech The new voice radio technology
     */
    @Override
    public void updatePhoneObject(int voiceRadioTech) {
        logd("updatePhoneObject do not need phone update in SVLTE case.");
    }

    @Override
    public void phoneObjectUpdater(int newVoiceRadioTech) {
        logd("phoneObjectUpdater do not need phone update in SVLTE case.");
    }

    @Override
    public void updatePsPhoneAndCi(PhoneBase psPhone) {
        super.updatePsPhoneAndCi(psPhone);
        mIratController.updatePsCi(psPhone.mCi);
        int psType = LteDcConstants.PS_SERVICE_UNKNOWN;
        if (psPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
            psType = LteDcConstants.PS_SERVICE_ON_LTE;
            logd("updatePsPhone: setBipPsType =" + LteDcConstants.PS_SERVICE_ON_LTE);
            mLtePhone.mCi.setBipPsType(LteDcConstants.PS_SERVICE_ON_LTE);
        } else if (psPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            psType = LteDcConstants.PS_SERVICE_ON_CDMA;
            logd("updatePsPhone: setBipPsType =" + LteDcConstants.PS_SERVICE_ON_CDMA);
            mLtePhone.mCi.setBipPsType(LteDcConstants.PS_SERVICE_ON_CDMA);
        }
        logd("updatePsPhoneAndCi: psType = " + psType);
        mIratDataSwitchHelper.setPsServiceType(psType);
    }

    @Override
    public void initialize() {
        // NOTE: Create DcTracker after set SVLTE phone proxy.
        createAndShareDcTracker();
        mIratController.setDcTracker(mSharedDcTracker);
        updatePsPhone(getDefaultPsPhone());
    }

    /**
     * Get IRAT data swtich helper of the phone proxy.
     * @return IRAT data swtich helper of the phone proxy.
     */
    public IratDataSwitchHelper getIratDataSwitchHelper() {
        return mIratDataSwitchHelper;
    }

    /**
     * Get IRAT controller of the phone proxy.
     * @return IRAT controller of the phone proxy.
     */
    public IratController getIratController() {
        return mIratController;
    }

    private void updateCsPhone(PhoneBase csPhone) {
        log("updateCsPhone: mCsPhone = " + mCsPhone + ", csPhone = " + csPhone);
        if (mCsPhone.getPhoneType() != csPhone.getPhoneType()) {
            mSharedDcTracker.updateCsPhoneForSvlte(csPhone);
            mCsPhone = csPhone;
        }
    }

    /**
     * Force to update PS phoen to the new PS phone.
     * @param psPhone New PS phone.
     */
    private void updatePsPhone(PhoneBase psPhone) {
        int psType = LteDcConstants.PS_SERVICE_UNKNOWN;
        if (psPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
            psType = LteDcConstants.PS_SERVICE_ON_LTE;
        } else if (psPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            psType = LteDcConstants.PS_SERVICE_ON_CDMA;
        }
        updatePsPhoneAndCi(psPhone);

        mSharedDcTracker.updatePhoneIfNeeded(psPhone);
        mSharedDcTracker.updateRecords();

        mIratDataSwitchHelper.setPsServiceType(psType);
        mIratController.setPsServiceType(psType);
    }

    @Override
    protected void switchActivePhone(Phone targetPhone) {
        updateIccSmsInterfaceManager(targetPhone);
        updateIccPhoneBookInterfaceManager(targetPhone);
        updateCsPhone((PhoneBase) targetPhone);
        updatePsPhone((PhoneBase) targetPhone);

        super.switchActivePhone(targetPhone);
        updateRegistrantsInfo(targetPhone);
    }

    private void updateIccSmsInterfaceManager(Phone targetPhone) {
        logd("updateIccSmsInterfaceManager: targetPhone = " + targetPhone);
        if (targetPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            mIccSmsInterfaceManager = mCdmaIccSmsInterfaceManager;
        } else if (targetPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
            mIccSmsInterfaceManager = mLteIccSmsInterfaceManager;
        }
    }

    private void updateIccPhoneBookInterfaceManager(Phone targetPhone) {
        logd("updateIccPhoneBookInterfaceManager: targetPhone = " + targetPhone);
        if (targetPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            mIccPhoneBookInterfaceManagerProxy = mCdmaIccPhoneBookInterfaceManagerProxy;
        } else if (targetPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
            mIccPhoneBookInterfaceManagerProxy = mLteIccPhoneBookInterfaceManagerProxy;
        }
    }

    @Override
    public ServiceState getSvlteServiceState() {
        boolean isInSvlteMode = (SvlteModeController
                .getRadioTechnologyMode(getPhoneId()) == SvlteModeController.RADIO_TECH_MODE_SVLTE);
        if (mNLtePhone != null && mNLtePhone.getServiceStateTracker() != null
                && isInSvlteMode) {
            return mNLtePhone.getServiceStateTracker().getSvlteServiceState();
        }
        return null;
    }

    @Override
    public void registerForMmiInitiate(Handler h, int what, Object obj) {
        if (SvlteUiccUtils.getInstance().isUsimCsimPhone(getPhoneId())) {
            logd("SvltePhoneProxy registerForMmiInitiate for usimcsim case");
            getLtePhone().registerForMmiInitiate(h, what, obj);
            //getNLtePhone().registerForMmiInitiate(h, what, obj);
        } else {
            logd("SvltePhoneProxy registerForMmiInitiate for activePhone case");
            mActivePhone.registerForMmiInitiate(h, what, obj);
        }
    }

    @Override
    public void unregisterForMmiInitiate(Handler h) {
        if (SvlteUiccUtils.getInstance().isUsimCsimPhone(getPhoneId())) {
            logd("SvltePhoneProxy unregisterForMmiInitiate for usimcsim case");
            getLtePhone().unregisterForMmiInitiate(h);
            //getNLtePhone().unregisterForMmiInitiate(h);
        } else {
            logd("SvltePhoneProxy unregisterForMmiInitiate for activePhone case");
            mActivePhone.unregisterForMmiInitiate(h);
        }
    }

    @Override
    public void registerForMmiComplete(Handler h, int what, Object obj) {
        if (SvlteUiccUtils.getInstance().isUsimCsimPhone(getPhoneId())) {
            logd("SvltePhoneProxy registerForMmiComplete for usimcsim case");
            getLtePhone().registerForMmiComplete(h, what, obj);
        } else {
            logd("SvltePhoneProxy registerForMmiComplete for activePhone case");
            mActivePhone.registerForMmiComplete(h, what, obj);
        }
    }

    @Override
    public void unregisterForMmiComplete(Handler h) {
        if (SvlteUiccUtils.getInstance().isUsimCsimPhone(getPhoneId())) {
            logd("SvltePhoneProxy unregisterForMmiComplete for usimcsim case");
            getLtePhone().unregisterForMmiComplete(h);
        } else {
            logd("SvltePhoneProxy unregisterForMmiComplete for activePhone case");
            mActivePhone.unregisterForMmiComplete(h);
        }
    }

    private void updateRegistrantsInfo(Phone targetPhone) {
        if (SvlteUiccUtils.getInstance().isUsimCsimPhone(getPhoneId())) {
            //getNLtePhone().migrate(getLtePhone().);
            logd("SvltePhoneProxy updateRegistrantsInfo: usim+csim.");
        } else {
            logd("SvltePhoneProxy updateRegistrantsInfo: donothing.");
        }
    }

    @Override
    public boolean handlePinMmi(String dialString) {
        if (SvlteUiccUtils.getInstance().isUsimCsimPhone(getPhoneId())) {
            logd("SvltePhoneProxy: use GSM phone to handlePinMmi: " + dialString);
            return getLtePhone().handlePinMmi(dialString);
        } else {
            return super.handlePinMmi(dialString);
        }
    }
}
