package com.mediatek.contacts.simcontact;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.PhoneConstants;
import com.android.contacts.common.R;
import com.mediatek.contacts.ContactsSystemProperties;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.telephony.TelephonyManagerEx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * M: [Gemini+] slot helper class. all slot related method placed here.
 */
public final class SlotUtils {
    private static final String TAG = "SlotUtils";

    private static final int PHONE_SLOT_NUM = 2/**PhoneConstants.GEMINI_SIM_NUM*/; // qinglei comment out for bp.
    private static final int FIRST_SLOT_ID = PhoneConstants.SIM_ID_1;

    private SlotUtils() {
    }

    /**
     * M: [Gemini+] each slot information defined in this class
     */
    private static final class SlotInfo {

        private static final String SIM_PHONE_BOOK_SERVICE_NAME_FOR_SINGLE_SLOT = "simphonebook";
        private static final String ICC_SDN_URI_FOR_SINGLE_SLOT = "content://icc/sdn";
        private static final String ICC_ADN_URI_FOR_SINGLE_SLOT = "content://icc/adn";
        private static final String ICC_PBR_URI_FOR_SINGLE_SLOT = "content://icc/pbr";

        int mSlotId;
        Uri mIccUri;
        Uri mIccUsimUri;
        Uri mSdnUri;
        String mVoiceMailNumber;
        String mSimPhoneBookServiceName;
        boolean mIsSlotServiceRunning = false;
        int mResId;
        PhbInfoWrapper mPhbInfo;

        public SlotInfo(int slotId) {
            mSlotId = slotId;
            generateIccUri();
            generateIccUsimUri();
            generateSdnUri();
            generateSimPhoneBook();
            updateVoiceMailNumber();
            generateResId();
            mPhbInfo = new PhbInfoWrapper(slotId);
        }

        /**
         * TODO: the resource should be limited to only one string
         */
        private void generateResId() {
            switch (mSlotId) {
            case 0:
                mResId = R.string.sim1;
                break;
            case 1:
                mResId = R.string.sim2;
                break;
            case 2:
                mResId = R.string.sim3;
                break;
            case 3:
                mResId = R.string.sim4;
                break;
            default:
                LogUtils.e(TAG, "[generateResId]no res for slot:" + mSlotId);
            }
        }

        /**
         * slot 0 ==> simphonebook slot 1 ==> simphonebook2
         */
        private void generateSimPhoneBook() {
            mSimPhoneBookServiceName = SIM_PHONE_BOOK_SERVICE_NAME_FOR_SINGLE_SLOT;
            if (mSlotId > 0) {
                mSimPhoneBookServiceName = mSimPhoneBookServiceName + (mSlotId + 1);
            }
        }

        public String getSimPhoneBookServiceName() {
            return mSimPhoneBookServiceName;
        }

        public void updateVoiceMailNumber() {
            if (SlotUtils.isGeminiEnabled()) {
                mVoiceMailNumber = TelephonyManagerEx.getDefault().getVoiceMailNumber(mSlotId);
            } else {
                mVoiceMailNumber = TelephonyManager.getDefault().getVoiceMailNumber();
            }
        }

        public String getVoiceMailNumber() {
            return mVoiceMailNumber;
        }

        private void generateSdnUri() {
            String str = ICC_SDN_URI_FOR_SINGLE_SLOT;
            if (isGeminiEnabled()) {
                // like:"content://icc/sdn2"
                str += (mSlotId + 1);
            }
            mSdnUri = Uri.parse(str);
        }

        private void generateIccUri() {
            String str = ICC_ADN_URI_FOR_SINGLE_SLOT;
            if (isGeminiEnabled()) {
                // like:"content://icc/adn2"
                str += (mSlotId + 1);
            }
            mIccUri = Uri.parse(str);
        }

        private void generateIccUsimUri() {
            String str = ICC_PBR_URI_FOR_SINGLE_SLOT;
            if (isGeminiEnabled()) {
                // like:"content://icc/pbr2"
                str += (mSlotId + 1);
            }
            mIccUsimUri = Uri.parse(str);
        }

        public void updateSimServiceRunningState(boolean isRunning) {
            LogUtils.i(TAG, "[updateSimServiceRunningState]slotid: " + mSlotId +
                    ",service running state changed from " + mIsSlotServiceRunning + " to "
                    + isRunning);
            mIsSlotServiceRunning = isRunning;
        }

        public boolean isSimServiceRunning() {
            return mIsSlotServiceRunning;
        }

        public Uri getIccUri() {
            return SimCardUtils.isSimUsimType(mSlotId) ? mIccUsimUri : mIccUri;
        }

        public Uri getSdnUri() {
            return mSdnUri;
        }

        public int getResId() {
            return mResId;
        }
    }

    private final static class PhbInfoWrapper {
        private int mSubId = SubInfoUtils.getInvalidSubId();
        private int mUsimGroupMaxNameLength;
        private int mUsimGroupMaxCount;
        private int mUsimAnrCount;
        private int mUsimEmailCount;
        private boolean mInitialized;

        public PhbInfoWrapper(int subId) {
            mSubId = subId;
            resetPhbInfo();
        }

        public void resetPhbInfo() {
            mUsimGroupMaxNameLength = -1;
            mUsimGroupMaxCount = 0;
            mUsimAnrCount = 0;
            mUsimEmailCount = 0;
            mInitialized = false;
        }

        public void refreshPhbInfo() {
            LogUtils.i(TAG, "[refreshPhbInfo]refreshing phb info for subId: " + mSubId);
            if (!SimCardUtils.isPhoneBookReady(mSubId)) {
                LogUtils.e(TAG, "[refreshPhbInfo]phb not ready, refresh aborted. slot: " + mSubId);
                mInitialized = false;
                return;
            }
            ///TODO: currently, only Usim is necessary for phb infos.
            if (!SimCardUtils.isSimUsimType(mSubId)) {
                LogUtils.i(TAG, "[refreshPhbInfo]not usim phb, nothing to refresh, keep default, subId: " + mSubId);
                mInitialized = true;
                return;
            }

            String serviceName = SubInfoUtils.getPhoneBookServiceName();
            try {
                final IIccPhoneBook iIccPhb = IIccPhoneBook.Stub.asInterface(ServiceManager.getService(serviceName));
                mUsimGroupMaxNameLength = iIccPhb.getUsimGrpMaxNameLen(mSubId);
                mUsimGroupMaxCount = iIccPhb.getUsimGrpMaxCount(mSubId);
                mUsimAnrCount = iIccPhb.getAnrCount(mSubId);
                mUsimEmailCount = iIccPhb.getEmailCount(mSubId);
            } catch (RemoteException e) {
                LogUtils.e(TAG, "[refreshPhbInfo]Exception happened when refreshing phb info");
                e.printStackTrace();
                mInitialized = false;
                return;
            }

            if (SimCardUtils.isSimUsimType(mSubId) && isAllZero()) {
                LogUtils.w(TAG, "[refreshPhbInfo] the query result is wrong.");
                mInitialized = false;
            } else {
                mInitialized = true;
            }

            LogUtils.i(TAG, "[refreshPhbInfo]refreshing done, UsimGroupMaxNameLenght = " + mUsimGroupMaxNameLength
                    + ", UsimGroupMaxCount = " + mUsimGroupMaxCount + ", UsimAnrCount = " + mUsimAnrCount
                    + ", UsimEmailCount = " + mUsimEmailCount);
        }

        private boolean isAllZero() {
            return (mUsimGroupMaxNameLength == 0 &&
                    mUsimGroupMaxCount == 0 && mUsimAnrCount == 0 && mUsimEmailCount == 0);
        }
        public int getUsimGroupMaxNameLength() {
            if (!mInitialized) {
                refreshPhbInfo();
            }
            LogUtils.d(TAG, "[getUsimGroupMaxNameLength] subId = " + mSubId + ", length = " + mUsimGroupMaxNameLength);
            return mUsimGroupMaxNameLength;
        }

        public int getUsimGroupMaxCount() {
            if (!mInitialized) {
                refreshPhbInfo();
            }
            LogUtils.d(TAG, "[getUsimGroupMaxCount] subId = " + mSubId + ", count = " + mUsimGroupMaxCount);
            return mUsimGroupMaxCount;
        }

        public int getUsimAnrCount() {
            if (!mInitialized) {
                refreshPhbInfo();
            }
            LogUtils.d(TAG, "[getUsimAnrCount] subId = " + mSubId + ", count = " + mUsimAnrCount);
            return mUsimAnrCount;
        }

        public int getUsimEmailCount() {
            if (!mInitialized) {
                refreshPhbInfo();
            }
            LogUtils.d(TAG, "[getUsimEmailCount] subId = " + mSubId + ", count = " + mUsimEmailCount);
            return mUsimEmailCount;
        }
    }

    private static HashMap<Integer, PhbInfoWrapper> sActiveUsimPhbInfoMap = new HashMap<Integer, PhbInfoWrapper>();

    public static void refreshActiveUsimPhbInfos() {
        sActiveUsimPhbInfoMap.clear();
        List<SubscriptionInfo> subscriptionInfoList = SubInfoUtils.getActivatedSubInfoList();
        if (subscriptionInfoList != null && subscriptionInfoList.size() > 0) {
            for (SubscriptionInfo subscriptionInfo : subscriptionInfoList) {
                sActiveUsimPhbInfoMap.put(subscriptionInfo.getSubscriptionId(),
                        new PhbInfoWrapper(subscriptionInfo.getSubscriptionId()));
            }
        }
    }

    @SuppressLint("UseSparseArrays")
    private static Map<Integer, SlotInfo> sSlotInfoMap = new HashMap<Integer, SlotInfo>();
    static {
        for (int i = 0; i < PHONE_SLOT_NUM; i++) {
            int slotId = FIRST_SLOT_ID + i;
            sSlotInfoMap.put(slotId, new SlotInfo(slotId));
        }
    }

    /**
     * M: get all slot Ids
     * @return the list contains all slot ids
     */
    public static List<Integer> getAllSlotIds() {
        return new ArrayList<Integer>(sSlotInfoMap.keySet());
    }

    /**
     * M: [Gemini+] get voice mail number for slot
     * @param slotId
     * @return string
     */
    public static String getVoiceMailNumberForSlot(int slotId) {
        if (isSlotValid(slotId)) {
            SlotInfo slotInfo = sSlotInfoMap.get(slotId);
            if (slotInfo != null) {
                return slotInfo.getVoiceMailNumber();
            } else {
                LogUtils.w(TAG, "[getVoiceMailNumberForSlot],slotId:" + slotId);
                return null;
            }
        }

        LogUtils.d(TAG, "[getVoiceMailNumberForSlot] slot " + slotId + " is not valid!");
        return null;
    }

    /**
     * M: [Gemini+] update the saved voice mail number
     */
    public static void updateVoiceMailNumber() {
        for (SlotInfo slot : sSlotInfoMap.values()) {
            slot.updateVoiceMailNumber();
        }
    }

    /**
     * M: [Gemini+] get current device total slot count
     * @return count
     */
    public static int getSlotCount() {
        return sSlotInfoMap.size();
    }

    /**
     * M: [Gemini+] check whether the slot is valid
     * @param slotId
     * @return true if valid
     */
    public static boolean isSlotValid(long slotId) {
        boolean isValid = sSlotInfoMap.containsKey(slotId);
        if (!isValid) {
            LogUtils.w(TAG, "[isSlotValid]slot " + slotId + " is invalid!");
        }
        return isValid;
    }

    /**
     * M: [Gemini+] slot ids are defined in array like 0, 1, 2, ...
     * @return the first id of all slotIds
     */
    public static int getFirstSlotId() {
        return FIRST_SLOT_ID;
    }

    /**
     * M: [Gemini+] get an invalid slot id, to indicate that this is not a sim slot.
     *
     * @return negative value
     */
    public static int getNonSlotId() {
        return -1;
    }

    /**
     * M: [Gemini+] in single card phone, the only slot has a slot id this method to
     * retrieve the id.
     *
     * @return the only slot id of a single card phone
     */
    public static int getSingleSlotId() {
        return FIRST_SLOT_ID;
    }

    /**
     * M: [Gemini+] get string resource id for the corresponding slot id
     * @param slotId
     * @return
     */
    public static int getResIdForSlot(int slotId) {
        SlotInfo slotInfo = sSlotInfoMap.get(slotId);
        if (slotInfo != null) {
            return slotInfo.getResId();
        } else {
            LogUtils.w(TAG, "[getResIdForSlot],slotId:" + slotId);
            return -1;
        }
    }

    /**
     * M: [Gemini+] resource is just string like "SIM1", "SIM2"
     * @param resId
     * @return if no slot matches, return NonSlotId
     */
    public static int getSlotIdFromSimResId(int resId) {
        for (int slotId : getAllSlotIds()) {
            if (sSlotInfoMap.get(slotId).mResId == resId) {
                return slotId;
            }
        }
        return getNonSlotId();
    }

    /**
     * M: [Gemini+] if gemini feature enabled on this device
     * @return
     */
    public static boolean isGeminiEnabled() {
        return ContactsSystemProperties.MTK_GEMINI_SUPPORT;
    }

    public static int getUsimGroupMaxNameLength(int subId) {
        PhbInfoWrapper usimPhbInfo = sActiveUsimPhbInfoMap.get(subId);
        if (usimPhbInfo != null) {
            return usimPhbInfo.getUsimGroupMaxNameLength();
        }
        return -1;
    }

    public static int getUsimGroupMaxCount(int subId) {
        PhbInfoWrapper usimPhbInfo = sActiveUsimPhbInfoMap.get(subId);
        if (usimPhbInfo != null) {
            return usimPhbInfo.getUsimGroupMaxCount();
        }
        return -1;
    }

    public static int getUsimAnrCount(int subId) {
        PhbInfoWrapper usimPhbInfo = sActiveUsimPhbInfoMap.get(subId);
        if (usimPhbInfo != null) {
            return usimPhbInfo.getUsimAnrCount();
        }
        return -1;
    }

    public static int getUsimEmailCount(int subId) {
        PhbInfoWrapper usimPhbInfo = sActiveUsimPhbInfoMap.get(subId);
        if (usimPhbInfo != null) {
            return usimPhbInfo.getUsimEmailCount();
        }
        return -1;
    }

    /**
     * Time Consuming, run in background
     * to refresh the PHB info, read from IccPhb, might access Modem, so, would be time consuming.
     * this must be called, once PHB state changed.
     *
     * @param slotId
     */
    public static void refreshPhbInfoBySlot(int slotId) {
        sSlotInfoMap.get(slotId).mPhbInfo.refreshPhbInfo();
    }

    /**
     * reset the PHB info cache to the un-init state.
     * this state means, any requirement trying to access the phb info, it would re-init
     * immediately.
     *
     * @param slotId
     */
    public static void resetPhbInfoBySlot(int slotId) {
        sSlotInfoMap.get(slotId).mPhbInfo.resetPhbInfo();
    }
}
