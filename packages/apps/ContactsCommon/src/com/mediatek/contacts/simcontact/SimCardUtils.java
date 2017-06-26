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
package com.mediatek.contacts.simcontact;


import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.ITelephony;
import com.google.common.annotations.VisibleForTesting;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.contacts.simservice.SIMServiceUtils;
import com.mediatek.contacts.util.LogUtils;

import java.util.HashMap;
import java.util.List;

import com.android.contacts.common.R;

public class SimCardUtils {
    private static final String TAG = "SimCardUtils";

    public interface SimType {
        String SIM_TYPE_USIM_TAG = "USIM";
        String SIM_TYPE_SIM_TAG = "SIM";
        String SIM_TYPE_UIM_TAG = "RUIM";
        String SIM_TYPE_CSIM_TAG = "CSIM";

        int SIM_TYPE_SIM = 0;
        int SIM_TYPE_USIM = 1;
        int SIM_TYPE_UIM = 2;
        int SIM_TYPE_CSIM = 3;
        int SIM_TYPE_UNKNOWN = -1;
    }

    public static boolean isSimPinRequest(long slotId) {
        Boolean v = (Boolean) getPresetObject(String.valueOf(slotId), SIM_KEY_WITHSLOT_PIN_REQUEST);
        if (v != null) {
            LogUtils.w(TAG, "[isSimPinRequest]slotId:" + slotId +
                    ",v:" + v);
            return v;
        }

        boolean isPinRequest = (TelephonyManager.SIM_STATE_PIN_REQUIRED == TelephonyManager
                    .getDefault().getSimState((int) slotId));
        LogUtils.d(TAG, "[isSimPinRequest]slotId:" + slotId +
                ",isPukRequest:" + isPinRequest);

        return isPinRequest;
    }

    public static boolean isSimStateReady(long slotId) {
        Boolean v = (Boolean) getPresetObject(String.valueOf(slotId), SIM_KEY_WITHSLOT_STATE_READY);
        if (v != null) {
            LogUtils.w(TAG, "[isSimStateReady]slotId:" + slotId +
                    ",v:" + v);
            return v;
        }

        boolean isSimStateReady = (TelephonyManager.SIM_STATE_READY == TelephonyManager
                    .getDefault().getSimState((int) slotId));
        LogUtils.d(TAG, "[isSimStateReady]slotId:" + slotId +
                ",isPukRequest:" + isSimStateReady);

        return isSimStateReady;
    }

    public static boolean isSimInserted(int slotId) {
        Boolean v = (Boolean) getPresetObject(String.valueOf(slotId), SIM_KEY_WITHSLOT_SIM_INSERTED);
        if (v != null) {
            LogUtils.w(TAG, "[isSimInserted]slotId:" + slotId +
                    ",v:" + v);
            return v;
        }

        final ITelephony iTel = ITelephony.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICE));
        boolean isSimInsert = false;
        try {
            if (iTel != null) {
                isSimInsert = iTel.hasIccCardUsingSlotId(slotId);
            }
        } catch (RemoteException e) {
            LogUtils.e(TAG, "[isSimInserted]catch exception:");
            e.printStackTrace();
            isSimInsert = false;
        }

        LogUtils.d(TAG, "[isSimInserted]slotId:" + slotId +
                ",isPukRequest:" + isSimInsert);

        return isSimInsert;
    }

    public static boolean isFdnEnabed(int slotId) {
        Boolean v = (Boolean) getPresetObject(String.valueOf(slotId), SIM_KEY_WITHSLOT_FDN_ENABLED);
        if (v != null) {
            LogUtils.w(TAG, "[isFdnEnabed]slotId:" + slotId +
                    ",v:" + v);
            return v;
        }

        final ITelephonyEx iTel = ITelephonyEx.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICE_EX));
        boolean isFdnEnabled = false;
        try {
            if (iTel != null) {
                if (SlotUtils.isGeminiEnabled()) {
                    isFdnEnabled = iTel.isFdnEnabled(slotId);
                } else {
                    isFdnEnabled = iTel.isFdnEnabled(0);
                }
            }
        } catch (RemoteException e) {
            LogUtils.e(TAG, "[isFdnEnabed]catch exception:");
            e.printStackTrace();
            isFdnEnabled = false;
        }

        LogUtils.d(TAG, "[isFdnEnabed]slotId:" + slotId +
                ",isFdnEnabled:" + isFdnEnabled);

        return isFdnEnabled;
    }

    public static boolean isSetRadioOn(ContentResolver resolver, int subId) {
        Boolean v = (Boolean) getPresetObject(String.valueOf(subId), SIM_KEY_WITHSLOT_SET_RADIO_ON);
        if (v != null) {
            LogUtils.w(TAG, "[isSetRadioOn]subId:" + subId +
                    ",v:" + v);
            return v;
        }

        boolean isRadioOn = false;
        if (SlotUtils.isGeminiEnabled()) {
            ///[Gemini+] dualSimSet rule: each bit stands for each slot radio status
            /// e.g. 0101 means only slot 0 & slot 2 is set radio on
            final int flagAllSimOn = (1 << SlotUtils.getSlotCount()) - 1;
            int dualSimSet = Settings.System.getInt(resolver, Settings.System.MSIM_MODE_SETTING, flagAllSimOn);
            isRadioOn = (Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 0)
                    && ((1 << (subId - SlotUtils.getFirstSlotId())) & dualSimSet) != 0;
        } else {
            isRadioOn = Settings.Global.getInt(resolver,
                    Settings.Global.AIRPLANE_MODE_ON, 0) == 0;
        }

        LogUtils.d(TAG, "[isSetRadioOn]subId:" + subId +
                ",isRadioOn:" + isRadioOn);

        return isRadioOn;
    }

    /**
     * check PhoneBook State is ready if ready, then return true.
     *
     * @param subId
     * @return
     */
    public static boolean isPhoneBookReady(int subId) {
        Boolean v = (Boolean) getPresetObject(String.valueOf(subId), SIM_KEY_WITHSLOT_PHB_READY);
        if (v != null) {
            LogUtils.w(TAG, "[isPhoneBookReady]subId:" + subId +
                    ",v:" + v);
            return v;
        }

        final ITelephonyEx telephonyEx = ITelephonyEx.Stub.asInterface(ServiceManager
              .getService("phoneEx"));

        if (null == telephonyEx) {
            LogUtils.w(TAG, "[isPhoneBookReady]phoneEx == null");
            return false;
        }

        boolean isPbReady = false;
        try {
            isPbReady = telephonyEx.isPhbReady(subId);
            LogUtils.d(TAG, "[isPhoneBookReady]isPbReady:" + isPbReady + "||subId:" + subId);
        } catch (RemoteException e) {
            LogUtils.e(TAG, "[isPhoneBookReady]catch exception:");
            e.printStackTrace();
        }

        LogUtils.d(TAG, "[isPhoneBookReady]subId:" + subId +
                ", isPbReady:" + isPbReady);
        return isPbReady;
    }

    /**
     * [Gemini+] get sim type integer by subId
     * sim type is integer defined in SimCardUtils.SimType
     * @param subId
     * @return SimCardUtils.SimType
     */
    public static int getSimTypeBySubId(int subId) {
        Integer v = (Integer) getPresetObject(String.valueOf(subId), SIM_KEY_WITHSLOT_SIM_TYPE);
        if (v != null) {
            LogUtils.w(TAG, "[getSimTypeBySubId]subId:" + subId +
                    ",v:" + v);
            return v;
        }
        int simType = -1;

        final ITelephonyEx iTel = ITelephonyEx.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICE_EX));
        if (iTel == null) {
            LogUtils.w(TAG, "[getSimTypeBySubId]iTel == null");
            return simType;
        }

        try {
            String iccCardType = iTel.getIccCardType(subId);
            if (SimType.SIM_TYPE_USIM_TAG.equals(iccCardType)) {
                simType = SimType.SIM_TYPE_USIM;
            } else if (SimType.SIM_TYPE_UIM_TAG.equals(iccCardType)) {
                simType = SimType.SIM_TYPE_UIM;
            } else if (SimType.SIM_TYPE_SIM_TAG.equals(iccCardType)) {
                simType = SimType.SIM_TYPE_SIM;
            } else if (SimType.SIM_TYPE_CSIM_TAG.equals(iccCardType)) {
                simType = SimType.SIM_TYPE_CSIM;
            }
        } catch (RemoteException e) {
            LogUtils.e(TAG, "[getSimTypeBySubId]catch exception:");
            e.printStackTrace();
        }

        LogUtils.d(TAG, "[getSimTypeBySubId]subId:" + subId +
                ",simType:" + simType);

        return simType;
    }

    public static String getIccCardType(int subId) {
        final ITelephonyEx iTel = ITelephonyEx.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICE_EX));
        if (iTel == null) {
            LogUtils.w(TAG, "[getIccCardType]iTel == null");
            return null;
        }

        String iccCardType = null;
        try {
            iccCardType = iTel.getIccCardType(subId);
        } catch (RemoteException e) {
            LogUtils.e(TAG, "[getIccCardType]catch exception:");
            e.printStackTrace();
        }

        LogUtils.d(TAG, "[getIccCardType]subId:" + subId +
                ",iccCardType:" + iccCardType);
        return iccCardType;
    }

    /**
     * [Gemini+] check whether a slot is insert a usim card
     * @param subId
     * @return true if it is usim card
     */
    public static boolean isSimUsimType(int subId) {
        Boolean v = (Boolean) getPresetObject(String.valueOf(subId), SIM_KEY_WITHSLOT_IS_USIM);
        if (v != null) {
            LogUtils.w(TAG, "[isSimUsimType]subId:" + subId +
                    ",v:" + v);
            return v;
        }

        boolean isUsim = false;
        final ITelephonyEx iTel = ITelephonyEx.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICE_EX));
        if (iTel == null) {
            LogUtils.w(TAG, "[isSimUsimType]iTel == null");
            return isUsim;
        }

        try {
            if (SimType.SIM_TYPE_USIM_TAG.equals(iTel.getIccCardType(subId))
                    || SimType.SIM_TYPE_CSIM_TAG.equals(iTel.getIccCardType(subId))) {
                isUsim = true;
            }
        } catch (RemoteException e) {
            LogUtils.e(TAG, "[isSimUsimType]catch exception:");
            e.printStackTrace();
        }

        LogUtils.d(TAG, "[isSimUsimType]subId:" + subId +
                ",isUsim:" + isUsim);

        return isUsim;
    }

    /**
     * For test
     */
    private static HashMap<String, ContentValues> sPresetSimData = null;

    @VisibleForTesting
    public static void clearPreSetSimData() {
        sPresetSimData = null;
    }

    private static Object getPresetObject(String key1, String key2) {
        if (sPresetSimData != null) {
            ContentValues values = sPresetSimData.get(key1);
            if (values != null) {
                Object v = values.get(key2);
                if (v != null) {
                    return v;
                }
            }
        }

        return null;
    }

    private static final String NO_SLOT = String.valueOf(-1);
    private static final String SIM_KEY_WITHSLOT_PUK_REQUEST = "isSimPukRequest";
    private static final String SIM_KEY_WITHSLOT_PIN_REQUEST = "isSimPinRequest";
    private static final String SIM_KEY_WITHSLOT_STATE_READY = "isSimStateReady";
    private static final String SIM_KEY_WITHSLOT_SIM_INSERTED = "isSimInserted";
    private static final String SIM_KEY_WITHSLOT_FDN_ENABLED = "isFdnEnabed";
    private static final String SIM_KEY_WITHSLOT_SET_RADIO_ON = "isSetRadioOn";
    private static final String SIM_KEY_WITHSLOT_PHB_READY = "isPhoneBookReady";
    private static final String SIM_KEY_WITHSLOT_SIM_TYPE = "getSimTypeBySlot";
    private static final String SIM_KEY_WITHSLOT_IS_USIM = "isSimUsimType";
    private static final String SIM_KEY_SIMINFO_READY = "isSimInfoReady";
    private static final String SIM_KEY_WITHSLOT_RADIO_ON = "isRadioOn";
    private static final String SIM_KEY_WITHSLOT_HAS_ICC_CARD = "hasIccCard";
    private static final String SIM_KEY_WITHSLOT_GET_SIM_INDICATOR_STATE = "getSimIndicatorState";

    @VisibleForTesting
    public static void preSetSimData(int slot, Boolean fdnEnabled,
            Boolean isUsim, Boolean phbReady, Boolean pinRequest,
            Boolean pukRequest, Boolean isRadioOn, Boolean isSimInserted,
            Integer simType, Boolean simStateReady, Boolean simInfoReady) {
        ContentValues value1 = new ContentValues();
        if (fdnEnabled != null) {
            value1.put(SIM_KEY_WITHSLOT_FDN_ENABLED, fdnEnabled);
        }
        if (isUsim != null) {
            value1.put(SIM_KEY_WITHSLOT_IS_USIM, isUsim);
        }
        if (phbReady != null) {
            value1.put(SIM_KEY_WITHSLOT_PHB_READY, phbReady);
        }
        if (pinRequest != null) {
            value1.put(SIM_KEY_WITHSLOT_PIN_REQUEST, pinRequest);
        }
        if (pukRequest != null) {
            value1.put(SIM_KEY_WITHSLOT_PUK_REQUEST, pukRequest);
        }
        if (isRadioOn != null) {
            value1.put(SIM_KEY_WITHSLOT_SET_RADIO_ON, isRadioOn);
        }
        if (isSimInserted != null) {
            value1.put(SIM_KEY_WITHSLOT_SIM_INSERTED, isSimInserted);
        }
        if (simType != null) {
            value1.put(SIM_KEY_WITHSLOT_SIM_TYPE, simType);
        }
        if (simStateReady != null) {
            value1.put(SIM_KEY_WITHSLOT_STATE_READY, simStateReady);
        }
        if (sPresetSimData == null) {
            sPresetSimData = new HashMap<String, ContentValues>();
        }
        if (value1 != null && value1.size() > 0) {
            String key1 = String.valueOf(slot);
            if (sPresetSimData.containsKey(key1)) {
                sPresetSimData.remove(key1);
            }
            sPresetSimData.put(key1, value1);
        }

        ContentValues value2 = new ContentValues();
        if (simInfoReady != null) {
            value2.put(SIM_KEY_SIMINFO_READY, simInfoReady);
        }
        if (value2 != null && value2.size() > 0) {
            if (sPresetSimData.containsKey(NO_SLOT)) {
                sPresetSimData.remove(NO_SLOT);
            }
            sPresetSimData.put(NO_SLOT, value2);
        }
    }

    public static class ShowSimCardStorageInfoTask extends AsyncTask<Void, Void, Void> {
        private static ShowSimCardStorageInfoTask sInstance = null;
        private boolean mIsCancelled = false;
        private boolean mIsException = false;
        private String mDlgContent = null;
        private Context mContext = null;
        private static boolean sNeedPopUp = false;
        private static HashMap<Integer, Integer> sSurplugMap = new HashMap<Integer, Integer>();

        public static void showSimCardStorageInfo(Context context, boolean needPopUp) {
            sNeedPopUp = needPopUp;
            LogUtils.i(TAG, "[ShowSimCardStorageInfoTask]_beg");
            if (sInstance != null) {
                sInstance.cancel();
                sInstance = null;
            }
            sInstance = new ShowSimCardStorageInfoTask(context);
            sInstance.execute();
            LogUtils.i(TAG, "[ShowSimCardStorageInfoTask]_end");
        }

        public ShowSimCardStorageInfoTask(Context context) {
            mContext = context;
            LogUtils.i(TAG, "[ShowSimCardStorageInfoTask] onCreate()");
        }

        @Override
        protected Void doInBackground(Void... args) {
            LogUtils.i(TAG, "[ShowSimCardStorageInfoTask]: doInBackground_beg");
            sSurplugMap.clear();
            List<SubscriptionInfo> subscriptionInfoList = SubInfoUtils.getActivatedSubInfoList();
            LogUtils.i(TAG, "[ShowSimCardStorageInfoTask]: subInfos.size = "
                    + SubInfoUtils.getActivatedSubInfoCount());
            if (!mIsCancelled && (subscriptionInfoList != null) && subscriptionInfoList.size() > 0) {
                StringBuilder build = new StringBuilder();
                int id = 0;
                for (SubscriptionInfo subscriptionInfo : subscriptionInfoList) {
                    if (id > 0) {
                        build.append("\n\n");
                    }
                    id++;
                    int[] storageInfos = null;
                    LogUtils.i(TAG, "[ShowSimCardStorageInfoTask] simName = "
                            + subscriptionInfo.getDisplayName()
                            + "; simSlot = " + subscriptionInfo.getSimSlotIndex()
                            + "; subId = " + subscriptionInfo.getSubscriptionId());
                    build.append(subscriptionInfo.getDisplayName());
                    build.append(":\n");
                    try {
                        ITelephonyEx phoneEx = ITelephonyEx.Stub.asInterface(ServiceManager
                              .checkService("phoneEx"));
                        if (!mIsCancelled && phoneEx != null) {
                            storageInfos = phoneEx.getAdnStorageInfo(subscriptionInfo.getSubscriptionId());
                            if (storageInfos == null) {
                                mIsException = true;
                                LogUtils.i(TAG, "[ShowSimCardStorageInfoTask]storageInfos is null.");
                                return null;
                            }
                            LogUtils.i(TAG, "[ShowSimCardStorageInfoTask] infos: "
                                    + storageInfos.toString());
                        } else {
                            LogUtils.i(TAG, "[ShowSimCardStorageInfoTask]: phone = null");
                            mIsException = true;
                            return null;
                        }
                    } catch (RemoteException ex) {
                        LogUtils.i(TAG, "[ShowSimCardStorageInfoTask]_exception: " + ex);
                        mIsException = true;
                        return null;
                    }
                    LogUtils.i(TAG, "subId:" + subscriptionInfo.getSubscriptionId() + "||storage:"
                            + (storageInfos == null ? "NULL" : storageInfos[1]) + "||used:"
                            + (storageInfos == null ? "NULL" : storageInfos[0]));
                    if (storageInfos != null && storageInfos[1] > 0) {
                        sSurplugMap.put(subscriptionInfo.getSubscriptionId(), storageInfos[1] - storageInfos[0]);
                    }
                    build.append(mContext.getResources().getString(R.string.dlg_simstorage_content,
                            storageInfos[1], storageInfos[0]));
                    if (mIsCancelled) {
                        return null;
                    }
                }
                mDlgContent = build.toString();
            }
            LogUtils.i(TAG, "[ShowSimCardStorageInfoTask]: doInBackground_end");
            return null;
        }

        public void cancel() {
            super.cancel(true);
            mIsCancelled = true;
            LogUtils.i(TAG, "[ShowSimCardStorageInfoTask]: mIsCancelled = true");
        }

        @Override
        protected void onPostExecute(Void v) {
            sInstance = null;
            mIsCancelled = false;
            mIsException = false;
        }

        public static int getSurplugCount(int subId) {
            LogUtils.d(TAG, "[getSurplugCount] sSurplugMap : " + sSurplugMap
                    + ",subId:" + subId);
            if (null != sSurplugMap && sSurplugMap.containsKey(subId)) {
                int result = sSurplugMap.get(subId);
                LogUtils.d(TAG, "[getSurplugCount] result : " + result);
                return result;
            } else {
                LogUtils.i(TAG, "[getSurplugCount] return -1");
                return -1;
            }
        }
    }

    /**
     * [Gemini+] wrapper gemini & common API
     *
     * @param subId
     *            the slot id
     * @return true if radio on
     */
    public static boolean isRadioOn(int subId) {
        Boolean v = (Boolean) getPresetObject(String.valueOf(subId), SIM_KEY_WITHSLOT_RADIO_ON);
        if (v != null) {
            LogUtils.w(TAG, "[isRadioOn]slotId:" + subId +
                    ",v:" + v);
            return v;
        }

        final ITelephony iTel = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
        if (iTel == null) {
            LogUtils.w(TAG, "[isRadioOn]iTel is null!");
            return false;
        }

        boolean isRadioOn = false;
        try {
            isRadioOn = iTel.isRadioOnForSubscriber(subId);
        } catch (RemoteException e) {
            LogUtils.e(TAG, "[isRadioOn] failed to get radio state for sub " + subId);
            e.printStackTrace();
            isRadioOn = false;
        }

        LogUtils.d(TAG, "[isRadioOn]subId:" + subId +
                "|isRadioOn:" + isRadioOn);

        return isRadioOn;
    }

    /**
     * [Gemini+] wrapper gemini & common API
     *
     * @param slotId
     * @return
     */
    public static boolean hasIccCard(int slotId) {
        Boolean v = (Boolean) getPresetObject(String.valueOf(slotId), SIM_KEY_WITHSLOT_HAS_ICC_CARD);
        if (v != null) {
            LogUtils.w(TAG, "[hasIccCard]slotId:" + slotId +
                    ",v:" + v);
            return v;
        }

        final ITelephony iTel = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
        if (iTel == null) {
            LogUtils.w(TAG, "[hasIccCard]iTel is null.");
            return false;
        }

        boolean hasIccCard = false;
        try {
            hasIccCard = iTel.hasIccCardUsingSlotId(slotId);
        } catch (RemoteException e) {
            LogUtils.e(TAG, "[hasIccCard] failed to check icc card state for slot " + slotId);
            e.printStackTrace();
            hasIccCard = false;
        }

        LogUtils.d(TAG, "[hasIccCard]slotId:" + slotId +
                "|hasIccCard:" + hasIccCard);

        return hasIccCard;
    }

    /**
     * M: [Gemini+] not only ready, but also idle for all sim operations its
     * requirement is: 1. iccCard is insert 2. radio is on 3. FDN is off 4. PHB
     * is ready 5. simstate is ready 6. simService is not running
     *
     * @param slotId
     *            the slotId to check
     * @return true if idle
     */
    public static boolean isSimStateIdle(int subId) {
        LogUtils.i(TAG, "[isSimStateIdle] subId: " + subId);
        if (!SubInfoUtils.checkSubscriber(subId)) {
            return false;
        }
        ///change for SIM Service Refactoring
        boolean isSimServiceRunning = SIMServiceUtils.isServiceRunning(subId);
        LogUtils.i(TAG, "[isSimStateIdle], isSimServiceRunning = " + isSimServiceRunning);
        return isPhoneBookReady(subId) && !isSimServiceRunning;
    }

    /** M: change for CR ALPS00707504 & ALPS00721348 @ {
     * remove condition about isSimServiceRunningOnSlot
     */
    public static boolean isSimReady(int subId) {
        boolean isPhoneBookReady = isPhoneBookReady(subId);
        LogUtils.i(TAG, "[isSimReady] isPhoneBookReady=" + isPhoneBookReady);
        return isPhoneBookReady;
    }
    /** @ } */

    /**
     * M: [Gemini+] wrapper gemini & common API
     *
     * @param slotId
     * @return
     */
    public static int getSimIndicatorState(int slotId) {
        Integer v = (Integer) getPresetObject(String.valueOf(slotId), SIM_KEY_WITHSLOT_GET_SIM_INDICATOR_STATE);
        if (v != null) {
            LogUtils.w(TAG, "[getSimIndicatorState]slotId:" + slotId +
                    ",v:" + v);
            return v;
        }

        final ITelephony iTel = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
        final ITelephonyEx iTelEx = ITelephonyEx.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
        if (iTel == null) {
            LogUtils.w(TAG, "[getSimIndicatorState]iTel is null.");
            return -1;
        }

        int simIndicatorState = -1;
        /** qinglei comment out for build pass
        try {
            if (SlotUtils.isGeminiEnabled()) {
                ///M:Lego API Refactoring
                simIndicatorState = iTelEx.getSimIndicatorState(slotId);
            } else {
                simIndicatorState = iTel.getSimIndicatorState();
            }
        } catch (RemoteException e) {
            LogUtils.e(TAG, "[getSimIndicatorState] failed to get sim indicator state for slot " + slotId);
            e.printStackTrace();
        }
        */

        LogUtils.d(TAG, "[getSimIndicatorState]slotId:" + slotId +
                "|simIndicatorState:" + simIndicatorState);

        return simIndicatorState;
    }

    /**
     * M: [Gemini+] wrapper gemini & common API
     *
     * @param input
     * @param subId
     * @return
     */
    public static boolean handlePinMmi(String input, int subId) {
        ///M:Lego Sim API Refactoring
        final ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
        if (phone == null) {
            LogUtils.w(TAG, "[handlePinMmi] fail to get phone for subId " + subId);
            return false;
        }
        boolean isHandled;
        try {
            isHandled = phone.handlePinMmiForSubscriber(subId, input);
        } catch (RemoteException e) {
            LogUtils.e(TAG, "[handlePinMmi]exception : ");
            e.printStackTrace();
            isHandled = false;
        }
        LogUtils.d(TAG, "[handlePinMmi]subId:" + subId + "|input:" + input +
                "|isHandled:" + isHandled);

        return isHandled;
    }

    /**
     * [Gemini+] get sim tag like "SIM", "USIM", "UIM" by slot id
     *
     * @param subId
     * @return
     */
    public static String getSimTagBySlot(int subId) {
        if (!SlotUtils.isSlotValid(subId)) {
            LogUtils.e(TAG, "[getSimTagBySlot]subId:" + subId + "is invalid!");
            return null;
        }
        final ITelephonyEx iTel = ITelephonyEx.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
        try {
            return iTel.getIccCardType(subId);
        } catch (RemoteException e) {
            LogUtils.e(TAG, "catched exception. subId: " + subId);
            e.printStackTrace();
        }

        return null;
    }

    /**
     * M: [Gemini+] when a sim card was turned on or off in Settings, a broadcast
     * Intent.ACTION_MSIM_MODE_CHANGED would be sent, and carry current Dual sim mode.
     * This method is defined to parse the intent, and check whether the slot is on or off.
     * @param slotId slot to check
     * @param the mode retrived from the broadcast intent
     * @return true if on, false if off
     */
    public static boolean isDualSimModeOn(int slotId, int mode) {
        assert (SlotUtils.isSlotValid(slotId));
        assert (mode >= 0 && mode < (1 << SlotUtils.getSlotCount()));
        return (1 << (slotId - SlotUtils.getFirstSlotId()) & mode) != 0;
    }

    /** M: Bug Fix for ALPS00557517 @{ */
    public static int getAnrCount(int subId) {
        return SlotUtils.getUsimAnrCount(subId);
    }
    /** @ } */

    /** M: Bug Fix for ALPS00566570 , get support email count
     * some USIM cards have no Email fields.
     * if the slot is invalid, return -1, otherwise return the email count
     * @{ */
    public static int getIccCardEmailCount(int subId) {
        return SlotUtils.getUsimEmailCount(subId);
    }
    /** @ } */

    /**
     * M: Check that whether the phone book is ready, and whether the sim card storage is full.
     * @param context the caller's context.
     * @param subId the slot to check.
     * @return true the phb is ready and sim card storage is OK,
     *         false the phb is not ready or sim card storage is full.
     */
    public static boolean checkPHBState(Context context, int subId) {
        long startTime = System.currentTimeMillis();
        boolean hitError = false;
        int errorToastId = -1;
        if (!isPhoneBookReady(subId)) {
            hitError = true;
            errorToastId = R.string.icc_phone_book_invalid;
        } else if (0 == ShowSimCardStorageInfoTask.getSurplugCount(subId)) {
            hitError = true;
            errorToastId = R.string.storage_full;
        }
        if (context == null) {
            Log.w(TAG, "[checkPHBState] context is null,subId:" + subId);
        }
        if (hitError && context != null) {
            Toast.makeText(context, errorToastId, Toast.LENGTH_LONG).show();
            LogUtils.d(TAG, "[checkPHBState] hitError=" + hitError);
        }
        return !hitError;
    }

    /**
     * Check that whether the phone book is ready only
     * @param context the caller's context.
     * @param subId the slot to check.
     * @return true the phb is ready false the phb is not ready
     */
    public static boolean isPhoneBookReady(Context context, int subId) {
        boolean hitError = false;
        int errorToastId = -1;
        if (!isPhoneBookReady(subId)) {
            hitError = true;
            errorToastId = R.string.icc_phone_book_invalid;
        }
        if (context == null) {
            Log.w(TAG, "[checkPHBState] context is null,subId:" + subId);
        }
        if (hitError && context != null) {
            Toast.makeText(context, errorToastId, Toast.LENGTH_LONG).show();
            LogUtils.d(TAG, "[checkPHBState] hitError=" + hitError);
        }
        return !hitError;
    }

    /**
     * Check subid and return the sim type value.
     * @param subId The sim card subid.
     * @return sim type string value.
     */
    public static String getSimTypeTagBySubId(int subId) {
        int simType = getSimTypeBySubId(subId);
        String value;
        switch (simType) {
            case SimType.SIM_TYPE_SIM:
                value = "SIM";
                break;
            case SimType.SIM_TYPE_USIM:
                value = "USIM";
                break;
            case SimType.SIM_TYPE_UIM:
                value = "UIM";
                break;
            case SimType.SIM_TYPE_CSIM:
                value = "UIM";
                break;
            default:
                value = "UNKNOWN";
                break;
        }
        LogUtils.d(TAG, "[getSimTypeTagBySubId] simType=" + simType + " | subId : " + subId
                + " | value : " + value);
        return value;
    }
}
