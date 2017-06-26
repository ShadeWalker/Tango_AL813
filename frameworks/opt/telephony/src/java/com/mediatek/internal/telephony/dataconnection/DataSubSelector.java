package com.mediatek.internal.telephony.dataconnection;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;

import android.provider.Settings;

import android.telephony.RadioAccessFamily;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.uicc.UiccController;

import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteModeController;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;
import com.mediatek.internal.telephony.RadioCapabilitySwitchUtil;
import com.mediatek.internal.telephony.uicc.SvlteUiccUtils;

import java.util.Arrays;

public class DataSubSelector {
    private static final boolean DBG = true;

    private int mPhoneNum;
    private boolean mIsNeedWaitImsi = false;
    private boolean mIsNeedWaitUnlock = false;

    private static final String PROPERTY_DEFAULT_DATA_ICCID = "persist.radio.data.iccid";
    private static final String PROPERTY_DEFAULT_SIMSWITCH_ICCID = "persist.radio.simswitch.iccid";
    private static final String NO_SIM_VALUE = "N/A";

    private static final boolean BSP_PACKAGE =
            SystemProperties.getBoolean("ro.mtk_bsp_package", false);

    private static String mOperatorSpec;
    private static final String OPERATOR_OM = "OM";
    private static final String OPERATOR_OP01 = "OP01";
    private static final String OPERATOR_OP02 = "OP02";
    private static final String OPERATOR_OP09 = "OP09";
    

    private static final String PROPERTY_3G_SIM = "persist.radio.simswitch";

    public static final String ACTION_MOBILE_DATA_ENABLE
            = "android.intent.action.ACTION_MOBILE_DATA_ENABLE";
    public static final String EXTRA_MOBILE_DATA_ENABLE_REASON = "reason";

    public static final String REASON_MOBILE_DATA_ENABLE_USER = "user";
    public static final String REASON_MOBILE_DATA_ENABLE_SYSTEM = "system";

    private static final String PROPERTY_MOBILE_DATA_ENABLE = "persist.radio.mobile.data";
    private String[] PROPERTY_ICCID = {
        "ril.iccid.sim1",
        "ril.iccid.sim2",
        "ril.iccid.sim3",
        "ril.iccid.sim4",
    };
    private Intent mIntent = null;
    private Context mContext = null;

    protected BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            log("onReceive: action=" + action);
            if (action.equals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED)) {
                mIsNeedWaitImsi = false;
                onSubInfoReady(intent);
            } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                String simStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                int slotId = intent.getIntExtra(PhoneConstants.SLOT_KEY, PhoneConstants.SIM_ID_1);
                log("slotId: " + slotId + " simStatus: " + simStatus + " mIsNeedWaitImsi: "
                        + mIsNeedWaitImsi + " mIsNeedWaitUnlock: " + mIsNeedWaitUnlock);
                if (simStatus.equals(IccCardConstants.INTENT_VALUE_ICC_IMSI)) {
                    if (mIsNeedWaitImsi == true) {
                        log("get imsi and need to check op01 again");
                        mIsNeedWaitImsi = false;
                        if (checkOp01CapSwitch() == false) {
                            mIsNeedWaitImsi = true;
                        }
                    } else if (mIsNeedWaitUnlock == true) {
                        log("get imsi because unlock");
                        
                        ITelephonyEx iTelEx = ITelephonyEx.Stub.asInterface(
                            ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
                        try {
                            if (iTelEx.isCapabilitySwitching()) {
                                // wait complete intent
                            } else {
                                mIsNeedWaitUnlock = false;
                                if (OPERATOR_OP01.equals(mOperatorSpec)) {
                                    subSelectorForOp01(mIntent);
                                } else if (OPERATOR_OP02.equals(mOperatorSpec)) {
                                    subSelectorForOp02(mIntent);
                                } else if (OPERATOR_OM.equals(mOperatorSpec)) {
                                    subSelectorForOm(mIntent);
                                } else if (OPERATOR_OP09.equals(mOperatorSpec)) {
                                    subSelectorForOp09(mIntent);
                                }
                            }
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } else if (action.equals(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_DONE)
                    || action.equals(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_FAILED)) {
                if (mIsNeedWaitUnlock == true) {
                    mIsNeedWaitUnlock = false;
                    if (OPERATOR_OP01.equals(mOperatorSpec)) {
                        subSelectorForOp01(mIntent);
                    } else if (OPERATOR_OP02.equals(mOperatorSpec)) {
                        subSelectorForOp02(mIntent);
                    } else if (OPERATOR_OM.equals(mOperatorSpec)) {
                        subSelectorForOm(mIntent);
                    } else if (OPERATOR_OP09.equals(mOperatorSpec)) {
                        subSelectorForOp09(mIntent);
                    }
                }
            } else if (action.equals(TelephonyIntents.ACTION_SET_RADIO_TECHNOLOGY_DONE)) {
                log("ACTION_SET_RADIO_TECHNOLOGY_DONE");
                if (mOperatorSpec.equals(OPERATOR_OM)) {
                    subSelectorForSvlte(intent);
                }
            }
        }
    };

    public DataSubSelector(Context context, int phoneNum) {
        log("DataSubSelector is created");
        mPhoneNum = phoneNum;
        mOperatorSpec = SystemProperties.get("ro.operator.optr", OPERATOR_OM);
        log("Operator Spec:" + mOperatorSpec);
        mContext = context;

        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_DONE);
        filter.addAction(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_FAILED);
        filter.addAction(TelephonyIntents.ACTION_SET_RADIO_TECHNOLOGY_DONE);
        context.registerReceiver(mBroadcastReceiver, filter);
    }

    private void onSubInfoReady(Intent intent) {

        if (BSP_PACKAGE) {
            log("Don't support BSP Package.");
            return;
        }
        if (mOperatorSpec.equals(OPERATOR_OP01)) {
            subSelectorForOp01(intent);
        } else if (mOperatorSpec.equals(OPERATOR_OP02)) {
            subSelectorForOp02(intent);
        } else if (mOperatorSpec.equals(OPERATOR_OP09)) {
            subSelectorForOp09(intent);
        } else if (CdmaFeatureOptionUtils.isCT6MSupport()) {
            subSelectorForOp09C(intent);
        } else if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            // wait for ACTION_SET_RADIO_TECHNOLOGY_DONE
            //Turn off data if new SIM detected.
            turnOffNewSimData(intent);
        } else {
            subSelectorForOm(intent);
        }

        updateDataEnableProperty();

    }

    private void subSelectorForSolution15(Intent intent) {
        log("DataSubSelector for C2K om solution 1.5: capability maybe diff with default data");

        // only handle 3/4G switching
        int phoneId = SubscriptionManager.INVALID_PHONE_INDEX;
        String[] currIccId = new String[mPhoneNum];

        //Since SvLTE Project may call subSelectorForOm before sub ready
        //we should do this on sub ready
        if (!CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            turnOffNewSimData(intent);
        }

        //Get previous sim switch data
        String capabilityIccid = SystemProperties.get(PROPERTY_DEFAULT_SIMSWITCH_ICCID);
        log("capability Iccid = " + capabilityIccid);
        String defaultIccid = SystemProperties.get(PROPERTY_DEFAULT_DATA_ICCID);
        log("Default data Iccid = " + defaultIccid);        
        for (int i = 0; i < mPhoneNum; i++) {
            currIccId[i] = SystemProperties.get(PROPERTY_ICCID[i]);
            if (currIccId[i] == null || "".equals(currIccId[i]) || "N/A".equals(currIccId[i])) {
                log("error: iccid not found, wait for next sub ready");
                return;
            }
            
            if (defaultIccid.equals(currIccId[i]) &&
                    SvlteUiccUtils.getInstance().isRuimCsim(i)) {
                phoneId = i;
                break;
            }            
            
            if (capabilityIccid.equals(currIccId[i])) {
                phoneId = i;
                break;
            }
        }

        log("capability  phoneid = " + phoneId);
        if (phoneId != SubscriptionManager.INVALID_PHONE_INDEX) {
            // always set capability to this phone
            setCapability(phoneId);
        }
    }

    private boolean checkCdmaCardInsert() {
        int phoneCount = TelephonyManager.getDefault().getPhoneCount();
        for (int i = 0; i< phoneCount; i++){
            if (SvlteUiccUtils.getInstance().isRuimCsim(i)){
                log("CDMA sim is inserted in " + i);
                return true;
            }
        }
        return false;
    }

    private void subSelectorForOm(Intent intent) {
        log("DataSubSelector for OM: only for capability switch; for default data, use google");

        // only handle 3/4G switching
        int phoneId = SubscriptionManager.INVALID_PHONE_INDEX;
        String[] currIccId = new String[mPhoneNum];

        //Since SvLTE Project may call subSelectorForOm before sub ready
        //we should do this on sub ready
        if (!CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            turnOffNewSimData(intent);
        }

        //Get previous default data
        String defaultIccid = SystemProperties.get(PROPERTY_DEFAULT_DATA_ICCID);
        log("Default data Iccid = " + defaultIccid);
        for (int i = 0; i < mPhoneNum; i++) {
            currIccId[i] = SystemProperties.get(PROPERTY_ICCID[i]);
            if (currIccId[i] == null || "".equals(currIccId[i])) {
                log("error: iccid not found, wait for next sub ready");
                return;
            }
            if (defaultIccid.equals(currIccId[i])) {
                phoneId = i;
                break;
            }

            if (NO_SIM_VALUE.equals(currIccId[i])) {
                log("clear mcc.mnc:" + i);
                String propStr;
                if (i == 0) {
                    propStr = "gsm.sim.ril.mcc.mnc";
                } else {
                    propStr = "gsm.sim.ril.mcc.mnc." + (i + 1);
                }
                SystemProperties.set(propStr, "");
            }
        }
        // check pin lock
        if (RadioCapabilitySwitchUtil.isAnySimLocked(mPhoneNum)) {
            log("DataSubSelector for OM: do not switch because of sim locking");
            mIsNeedWaitUnlock = true;
            mIntent = intent;
            return;
        }

        log("Default data phoneid = " + phoneId);
        if (phoneId != SubscriptionManager.INVALID_PHONE_INDEX) {
            // always set capability to this phone
            setCapability(phoneId);
        }
    }

    /*private void subSelectorForOm(Intent intent) {
        int phoneId = SubscriptionManager.INVALID_PHONE_INDEX;
        int insertedSimCount = 0;
        int insertedStatus = 0;
        int detectedType = intent.getIntExtra(SubscriptionManager.INTENT_KEY_DETECT_STATUS, 0);
        String[] currIccId = new String[mPhoneNum];

        log("DataSubSelector for OM");

        for (int i = 0; i < mPhoneNum; i++) {
            currIccId[i] = SystemProperties.get(PROPERTY_ICCID + (i + 1));
            if (!NO_SIM_VALUE.equals(currIccId[i])) {
                ++insertedSimCount;
                insertedStatus = insertedStatus | (1 << i);
            }
        }
        log("Inserted SIM count: " + insertedSimCount + ", insertedStatus: " + insertedStatus);

        //Get previous default data
        String defaultIccid = SystemProperties.get(PROPERTY_DEFAULT_DATA_ICCID);
        log("Default data Iccid = " + defaultIccid);

        if (insertedSimCount == 0) {
            // No SIM inserted
            // 1. Default Data: unset
            // 2. Data Enable: OFF
            // 3. 34G: No change
            log("C0: No SIM inserted, set data unset");
            setDefaultData(SubscriptionManager.INVALID_PHONE_INDEX);
        } else if (insertedSimCount == 1) {
            for (int i = 0; i < mPhoneNum; i++) {
                if ((insertedStatus & (1 << i)) != 0) {
                    phoneId = i;
                    break;
                }
            }

            if (detectedType == SubscriptionManager.EXTRA_VALUE_NEW_SIM) {
                // Case 1: Single SIM + New SIM:
                // 1. Default Data: this sub
                // 2. Data Enable: OFF
                // 3. 34G: this sub
                log("C1: Single SIM + New SIM: Set Default data to phone:" + phoneId);
                if (setCapability(phoneId)) {
                    setDefaultData(phoneId);
                }
                setDataEnable(false);
            } else {
                if (defaultIccid == null || "".equals(defaultIccid)) {
                    //It happened from two SIMs without default SIM -> remove one SIM.
                    // Case 3: Single SIM + Non Data SIM:
                    // 1. Default Data: this sub
                    // 2. Data Enable: OFF
                    // 3. 34G: this sub
                    log("C3: Single SIM + Non Data SIM: Set Default data to phone:" + phoneId);
                    if (setCapability(phoneId)) {
                        setDefaultData(phoneId);
                    }
                    setDataEnable(false);
                } else {
                    if (defaultIccid.equals(currIccId[phoneId])) {
                        // Case 2: Single SIM + Defult Data SIM:
                        // 1. Default Data: this sub
                        // 2. Data Enable: No Change
                        // 3. 34G: this sub
                        log("C2: Single SIM + Data SIM: Set Default data to phone:" + phoneId);
                        if (setCapability(phoneId)) {
                            setDefaultData(phoneId);
                        }
                    } else {
                        // Case 3: Single SIM + Non Data SIM:
                        // 1. Default Data: this sub
                        // 2. Data Enable: OFF
                        // 3. 34G: this sub
                        log("C3: Single SIM + Non Data SIM: Set Default data to phone:" + phoneId);
                        if (setCapability(phoneId)) {
                            setDefaultData(phoneId);
                        }
                        setDataEnable(false);
                    }
                }
            }
        } else if (insertedSimCount >= 2) {
            if (detectedType == SubscriptionManager.EXTRA_VALUE_NEW_SIM) {
                int newSimStatus = intent.getIntExtra(
                        SubscriptionManager.INTENT_KEY_NEW_SIM_SLOT, 0);

                boolean isAllNewSim = true;
                for (int i = 0; i < mPhoneNum; i++) {
                    if ((newSimStatus & (1 << i)) == 0) {
                        isAllNewSim = false;
                    }
                }

                if (isAllNewSim) {
                    // Case 4: Multi SIM + All New SIM:
                    // 1. Default Data: Unset
                    // 2. Data Enable: OFF
                    // 3. 34G: Sub1
                    log("C4: Multi SIM + All New SIM: Set 34G to sub1");
                    if (setCapability(PhoneConstants.SIM_ID_1)) {
                        setDefaultData(SubscriptionManager.INVALID_PHONE_INDEX);
                    }
                    setDataEnable(false);
                } else {
                    if (defaultIccid == null || "".equals(defaultIccid)) {
                        //Not found previous default SIM, don't change.
                        // Case 6: Multi SIM + New SIM + Non Default SIM:
                        // 1. Default Data: Unset
                        // 2. Data Enable: OFF
                        // 3. 34G: No Change
                        log("C6: Multi SIM + New SIM + Non Default SIM: No Change");
                        setDefaultData(SubscriptionManager.INVALID_PHONE_INDEX);
                        setDataEnable(false);
                    } else {
                        for (int i = 0; i < mPhoneNum; i++) {
                            if (defaultIccid.equals(currIccId[i])) {
                                phoneId = i;
                                break;
                            }
                        }

                        if (phoneId != SubscriptionManager.INVALID_PHONE_INDEX) {
                            // Case 5: Multi SIM + New SIM + Default SIM:
                            // 1. Default Data: Default SIM
                            // 2. Data Enable: No Change
                            // 3. 34G: Default SIM
                            log("C5: Multi SIM + New SIM + Default SIM: Set Default data to "
                                + "phone:" + phoneId);
                            if (setCapability(phoneId)) {
                                setDefaultData(phoneId);
                            }
                        } else {
                            // Case 6: Multi SIM + New SIM + Non Default SIM:
                            // 1. Default Data: Unset
                            // 2. Data Enable: OFF
                            // 3. 34G: No Change
                            log("C6: Multi SIM + New SIM + Non Default SIM: No Change");
                            setDefaultData(SubscriptionManager.INVALID_PHONE_INDEX);
                            setDataEnable(false);
                        }
                    }
                }
            } else {
                if (defaultIccid == null || "".equals(defaultIccid)) {
                    //Case 8: Multi SIM + All Old SIM + No Default SIM:
                    // 1. Default Data: Unset
                    // 2. Data Enable: No Change
                    // 3. 34G: No change
                    //Do nothing
                    log("C8: Do nothing");
                } else {
                    for (int i = 0; i < mPhoneNum; i++) {
                        if (defaultIccid.equals(currIccId[i])) {
                            phoneId = i;
                            break;
                        }
                    }
                    if (phoneId != SubscriptionManager.INVALID_PHONE_INDEX) {
                        // Case 7: Multi SIM + All Old SIM + Default SIM:
                        // 1. Default Data: Default SIM
                        // 2. Data Enable: No Change
                        // 3. 34G: Default SIM
                        log("C7: Multi SIM + New SIM + Default SIM: Set Default data to phone:"
                                + phoneId);
                        if (setCapability(phoneId)) {
                            setDefaultData(phoneId);
                        }
                    } else {
                        //Case 8: Multi SIM + All Old SIM + No Default SIM:
                        // 1. Default Data: Unset
                        // 2. Data Enable: No Change
                        // 3. 34G: No change
                        //Do nothing
                        log("C8: Do nothing");
                    }
                }
            }
        }
    }*/

    private void subSelectorForOp02(Intent intent) {
        int phoneId = SubscriptionManager.INVALID_PHONE_INDEX;
        int insertedSimCount = 0;
        int insertedStatus = 0;
        int detectedType = intent.getIntExtra(SubscriptionManager.INTENT_KEY_DETECT_STATUS, 0);
        String[] currIccId = new String[mPhoneNum];

        log("DataSubSelector for OP02");

        for (int i = 0; i < mPhoneNum; i++) {
            currIccId[i] = SystemProperties.get(PROPERTY_ICCID[i]);
            if (currIccId[i] == null || "".equals(currIccId[i])) {
                log("error: iccid not found, wait for next sub ready");
                return;
            }
            if (!NO_SIM_VALUE.equals(currIccId[i])) {
                ++insertedSimCount;
                insertedStatus = insertedStatus | (1 << i);
            } else {
                log("clear mcc.mnc:" + i);
                String propStr;
                if (i == 0) {
                    propStr = "gsm.sim.ril.mcc.mnc";
                } else {
                    propStr = "gsm.sim.ril.mcc.mnc." + (i + 1);
                }
                SystemProperties.set(propStr, "");
            }
        }
        // check pin lock
        if (RadioCapabilitySwitchUtil.isAnySimLocked(mPhoneNum)) {
            log("DataSubSelector for OP02: do not switch because of sim locking");
            mIsNeedWaitUnlock = true;
            mIntent = intent;
            return;
        }

        log("Inserted SIM count: " + insertedSimCount + ", insertedStatus: " + insertedStatus);

        if (detectedType == SubscriptionManager.EXTRA_VALUE_NOCHANGE) {
            // OP02 Case 0: No SIM change, do nothing
            log("OP02 C0: Inserted status no change, do nothing");
        } else if (insertedSimCount == 0) {
            // OP02 Case 1: No SIM inserted
            // 1. Default Data: unset
            // 2. Data Enable: No Change
            // 3. 34G: Always SIM1
            log("OP02 C1: No SIM inserted, set data unset");
            setDefaultData(SubscriptionManager.INVALID_PHONE_INDEX);
        } else if (insertedSimCount == 1) {
            for (int i = 0; i < mPhoneNum; i++) {
                if ((insertedStatus & (1 << i)) != 0) {
                    phoneId = i;
                    break;
                }
            }
            //OP02 Case 2: Single SIM
            // 1. Default Data: This sub
            // 2. Data Enable: No Change
            // 3. 34G: Always SIM1
            log("OP02 C2: Single SIM: Set Default data to phone:" + phoneId);
            setDefaultData(phoneId);

            // Set data enabled for phoneId if the data of the other phone is enabled orginally
            String strEnabled = "0";
            if (phoneId == PhoneConstants.SIM_ID_1) {
                strEnabled = TelephonyManager.getDefault().getTelephonyProperty(
                        PhoneConstants.SIM_ID_2, PROPERTY_MOBILE_DATA_ENABLE, "0");
            } else {
                strEnabled = TelephonyManager.getDefault().getTelephonyProperty(
                        PhoneConstants.SIM_ID_1, PROPERTY_MOBILE_DATA_ENABLE, "0");
            }
            if (!strEnabled.equals("0") && strEnabled.length() > 1) {
                setDataEnabled(phoneId, true);
            }
        } else if (insertedSimCount >= 2) {
            //OP02 Case 3: Multi SIM
            // 1. Default Data: Always SIM1
            // 2. Data Enable: No Change
            // 3. 34G: Always SIM1
            log("OP02 C3: Multi SIM: Set Default data to phone1");
            setDefaultData(PhoneConstants.SIM_ID_1);

            // Set data disabled for sim2
            // But before that, we should set data enabled for sim1
            // if the original sim2's data is enabled
            int phone2SubId = PhoneFactory.getPhone(PhoneConstants.SIM_ID_2).getSubId();
            TelephonyManager telephony = TelephonyManager.getDefault();
            if (telephony != null && telephony.getDataEnabled(phone2SubId)) {
                setDataEnabled(PhoneConstants.SIM_ID_1, true);
            }
            setDataEnabled(PhoneConstants.SIM_ID_2, false);
        }
    }

    private void subSelectorForOp01(Intent intent) {
        int phoneId = SubscriptionManager.INVALID_PHONE_INDEX;
        int insertedSimCount = 0;
        int insertedStatus = 0;
        int detectedType = intent.getIntExtra(SubscriptionManager.INTENT_KEY_DETECT_STATUS, 0);
        String[] currIccId = new String[mPhoneNum];

        log("DataSubSelector for op01");

        for (int i = 0; i < mPhoneNum; i++) {
            currIccId[i] = SystemProperties.get(PROPERTY_ICCID[i]);
            if (currIccId[i] == null || "".equals(currIccId[i])) {
                log("error: iccid not found, wait for next sub ready");
                return;
            }
            log("currIccId[" + i + "] : " + currIccId[i]);
            if (!NO_SIM_VALUE.equals(currIccId[i])) {
                ++insertedSimCount;
                insertedStatus = insertedStatus | (1 << i);
            } else {
                log("clear mcc.mnc:" + i);
                String propStr;
                if (i == 0) {
                    propStr = "gsm.sim.ril.mcc.mnc";
                } else {
                    propStr = "gsm.sim.ril.mcc.mnc." + (i + 1);
                }
                SystemProperties.set(propStr, "");
            }
        }
        // check pin lock
        if (RadioCapabilitySwitchUtil.isAnySimLocked(mPhoneNum)) {
            log("DataSubSelector for OP01: do not switch because of sim locking");
            mIsNeedWaitUnlock = true;
            mIntent = intent;
            return;
        }

        log("Inserted SIM count: " + insertedSimCount + ", insertedStatus: " + insertedStatus);

        //Get previous default data
        String defaultIccid = SystemProperties.get(PROPERTY_DEFAULT_DATA_ICCID);
        log("Default data Iccid = " + defaultIccid);

        if (insertedSimCount == 0) {
            // No SIM inserted
            // 1. Default Data: unset
            // 2. Data Enable: OFF
            // 3. 34G: No change
            log("C0: No SIM inserted, set data unset");
            setDefaultData(SubscriptionManager.INVALID_PHONE_INDEX);
        } else if (insertedSimCount == 1) {
            for (int i = 0; i < mPhoneNum; i++) {
                if ((insertedStatus & (1 << i)) != 0) {
                    phoneId = i;
                    break;
                }
            }

            if (detectedType == SubscriptionManager.EXTRA_VALUE_NEW_SIM) {
                // Case 1: Single SIM + New SIM:
                // 1. Default Data: this sub
                // 2. Data Enable: OFF
                // 3. 34G: this sub
                log("C1: Single SIM + New SIM: Set Default data to phone:" + phoneId);
                if (setCapability(phoneId)) {
                    setDefaultData(phoneId);
                }
                setDataEnabled(phoneId, false);
            } else {
                if (defaultIccid == null || "".equals(defaultIccid)) {
                    //It happened from two SIMs without default SIM -> remove one SIM.
                    // Case 3: Single SIM + Non Data SIM:
                    // 1. Default Data: this sub
                    // 2. Data Enable: OFF
                    // 3. 34G: this sub
                    log("C3: Single SIM + Non Data SIM: Set Default data to phone:" + phoneId);
                    if (setCapability(phoneId)) {
                        setDefaultData(phoneId);
                    }
                    setDataEnabled(phoneId, false);
                } else {
                    if (defaultIccid.equals(currIccId[phoneId])) {
                        // Case 2: Single SIM + Defult Data SIM:
                        // 1. Default Data: this sub
                        // 2. Data Enable: No Change
                        // 3. 34G: this sub
                        log("C2: Single SIM + Data SIM: Set Default data to phone:" + phoneId);
                        if (setCapability(phoneId)) {
                            setDefaultData(phoneId);
                        }
                    } else {
                        // Case 3: Single SIM + Non Data SIM:
                        // 1. Default Data: this sub
                        // 2. Data Enable: OFF
                        // 3. 34G: this sub
                        log("C3: Single SIM + Non Data SIM: Set Default data to phone:" + phoneId);
                        if (setCapability(phoneId)) {
                            setDefaultData(phoneId);
                        }
                        setDataEnabled(phoneId, false);
                    }
                }
            }
        }
        else if (insertedSimCount >= 2) {
            if (detectedType == SubscriptionManager.EXTRA_VALUE_NEW_SIM) {
                int newSimStatus = intent.getIntExtra(
                        SubscriptionManager.INTENT_KEY_NEW_SIM_SLOT, 0);

                boolean isAllNewSim = true;
                for (int i = 0; i < mPhoneNum; i++) {
                    if ((newSimStatus & (1 << i)) == 0) {
                        isAllNewSim = false;
                    }
                }

                if (isAllNewSim) {
                    // Case 4: Multi SIM + All New SIM:
                    // 1. Default Data: Unset
                    // 2. Data Enable: OFF
                    // 3. 34G: Sub1
                    log("C4: Multi SIM + All New SIM: Set 34G to sub1");
                    setDefaultData(SubscriptionManager.INVALID_PHONE_INDEX);
                    setDataEnabled(PhoneConstants.SIM_ID_1, false);
                    setDataEnabled(PhoneConstants.SIM_ID_2, false);
                } else {
                    if (defaultIccid == null || "".equals(defaultIccid)) {
                        //Not found previous default SIM, don't change.
                        // Case 6: Multi SIM + New SIM + Non Default SIM:
                        // 1. Default Data: Unset
                        // 2. Data Enable: OFF
                        // 3. 34G: No Change
                        log("C6: Multi SIM + New SIM + Non Default SIM: No Change");
                        setDefaultData(SubscriptionManager.INVALID_PHONE_INDEX);
                        setDataEnabled(PhoneConstants.SIM_ID_1, false);
                        setDataEnabled(PhoneConstants.SIM_ID_2, false);
                    } else {
                        for (int i = 0; i < mPhoneNum; i++) {
                            if (defaultIccid.equals(currIccId[i])) {
                                phoneId = i;
                                break;
                            }
                        }

                        if (phoneId != SubscriptionManager.INVALID_PHONE_INDEX) {
                            // Case 5: Multi SIM + New SIM + Default SIM:
                            // 1. Default Data: Default SIM
                            // 2. Data Enable: No Change
                            // 3. 34G: Default SIM
                            log("C5: Multi SIM + New SIM + Default SIM: Set Default data to "
                                + "phone:" + phoneId);
                            setDefaultData(phoneId);

                            // Set the data status of non-default sim to false
                            int nonDefaultPhoneId = 0;
                            if (phoneId == 0) {
                                nonDefaultPhoneId = 1;
                            } else {
                                nonDefaultPhoneId = 0;
                            }
                            setDataEnabled(nonDefaultPhoneId, false);
                        } else {
                            // Case 6: Multi SIM + New SIM + Non Default SIM:
                            // 1. Default Data: Unset
                            // 2. Data Enable: OFF
                            // 3. 34G: No Change
                            log("C6: Multi SIM + New SIM + Non Default SIM: No Change");
                            setDefaultData(SubscriptionManager.INVALID_PHONE_INDEX);
                            setDataEnabled(PhoneConstants.SIM_ID_1, false);
                            setDataEnabled(PhoneConstants.SIM_ID_2, false);
                        }
                    }
                }
            } else {
                if (defaultIccid == null || "".equals(defaultIccid)) {
                    //Case 8: Multi SIM + All Old SIM + No Default SIM:
                    // 1. Default Data: Unset
                    // 2. Data Enable: No Change
                    // 3. 34G: No change
                    //Do nothing
                    loge("C8: Do nothing");
                } else {
                    for (int i = 0; i < mPhoneNum; i++) {
                        if (defaultIccid.equals(currIccId[i])) {
                            phoneId = i;
                            break;
                        }
                    }
                    if (phoneId != SubscriptionManager.INVALID_PHONE_INDEX) {
                        // Case 7: Multi SIM + All Old SIM + Default SIM:
                        // 1. Default Data: Default SIM
                        // 2. Data Enable: No Change
                        // 3. 34G: Default SIM
                        log("C7: Multi SIM + All Old SIM + Default SIM: Set Default data to phone:"
                                + phoneId);
                        setDefaultData(phoneId);

                        // Set the data status of non-default sim to false
                        int nonDefaultPhoneId = 0;
                        if (phoneId == 0) {
                            nonDefaultPhoneId = 1;
                        } else {
                            nonDefaultPhoneId = 0;
                        }
                        setDataEnabled(nonDefaultPhoneId, false);
                    } else {
                        //Case 8: Multi SIM + All Old SIM + No Default SIM:
                        // 1. Default Data: Unset
                        // 2. Data Enable: No Change
                        // 3. 34G: No change
                        //Do nothing
                        loge("C8: Do nothing");
                    }
                }
            }
            if (checkOp01CapSwitch() == false) {
                // need wait imsi ready
                mIsNeedWaitImsi = true;
                mIntent = intent;
                return;
            }
        }
    }

    private boolean checkOp01CapSwitch() {
        // check if need to switch capability
        // op01 USIM > op01 SIM > oversea USIM > oversea SIM > others
        int[] simOpInfo = new int[mPhoneNum];
        int[] simType = new int[mPhoneNum];
        int targetSim = -1;
        int insertedSimCount = 0;
        int insertedStatus = 0;
        boolean[] op01Usim = new boolean[mPhoneNum];
        boolean[] op01Sim = new boolean[mPhoneNum];
        boolean[] overseaUsim = new boolean[mPhoneNum];
        boolean[] overseaSim = new boolean[mPhoneNum];
        String capabilitySimIccid = SystemProperties.get(RadioCapabilitySwitchUtil.MAIN_SIM_PROP);
        String[] currIccId = new String[mPhoneNum];

        log("checkOp01CapSwitch start");

        for (int i = 0; i < mPhoneNum; i++) {
            currIccId[i] = SystemProperties.get(PROPERTY_ICCID[i]);
            if (currIccId[i] == null || "".equals(currIccId[i])) {
                log("error: iccid not found, wait for next sub ready");
                return false;
            }
            if (!NO_SIM_VALUE.equals(currIccId[i])) {
                ++insertedSimCount;
                insertedStatus = insertedStatus | (1 << i);
            }
        }
        log("checkOp01CapSwitch : Inserted SIM count: " + insertedSimCount
                + ", insertedStatus: " + insertedStatus);
        if (RadioCapabilitySwitchUtil.getSimInfo(simOpInfo, simType, insertedStatus) == false) {
            return false;
        }
        // check pin lock
        String propStr;
        for (int i = 0; i < mPhoneNum; i++) {
            if (i == 0) {
                propStr = "gsm.sim.ril.mcc.mnc";
            } else {
                propStr = "gsm.sim.ril.mcc.mnc." + (i + 1);
            }
            if (SystemProperties.get(propStr, "").equals("sim_lock")) {
                log("checkOp01CapSwitch : phone " + i + " is sim lock");
                mIsNeedWaitUnlock = true;
            }
        }
        int capabilitySimId = Integer.valueOf(
                SystemProperties.get(PhoneConstants.PROPERTY_CAPABILITY_SWITCH, "1")) - 1;
        log("op01: capabilitySimIccid:" + capabilitySimIccid
                + "capabilitySimId:" + capabilitySimId);
        for (int i = 0; i < mPhoneNum; i++) {
            // update SIM status
            if (simOpInfo[i] == RadioCapabilitySwitchUtil.SIM_OP_INFO_OP01) {
                if (simType[i] != RadioCapabilitySwitchUtil.SIM_TYPE_SIM) {
                    op01Usim[i] = true;
                } else {
                    op01Sim[i] = true;
                }
            } else if (simOpInfo[i] == RadioCapabilitySwitchUtil.SIM_OP_INFO_OVERSEA) {
                if (simType[i] != RadioCapabilitySwitchUtil.SIM_TYPE_SIM) {
                    overseaUsim[i] = true;
                } else {
                    overseaSim[i] = true;
                }
            }
        }
        // dump sim op info
        log("op01Usim: " + Arrays.toString(op01Usim));
        log("op01Sim: " + Arrays.toString(op01Sim));
        log("overseaUsim: " + Arrays.toString(overseaUsim));
        log("overseaSim: " + Arrays.toString(overseaSim));

        for (int i = 0; i < mPhoneNum; i++) {
            if (capabilitySimIccid.equals(currIccId[i])) {
                targetSim = RadioCapabilitySwitchUtil.getHigherPrioritySimForOp01(i, op01Usim
                        , op01Sim, overseaUsim, overseaSim);
                log("op01: i = " + i + ", currIccId : " + currIccId[i] + ", targetSim : " + targetSim);
                // default capability SIM is inserted
                if (op01Usim[i] == true) {
                    log("op01-C1: cur is old op01 USIM, no change");
                    if (capabilitySimId != i) {
                        log("op01-C1a: old op01 USIM change slot, change!");
                        setCapability(i);
                    }
                    return true;
                } else if (op01Sim[i] == true) {
                    if (targetSim != -1) {
                        log("op01-C2: cur is old op01 SIM but find op01 USIM, change!");
                        setCapability(targetSim);
                    } else if (capabilitySimId != i) {
                        log("op01-C2a: old op01 SIM change slot, change!");
                        setCapability(i);
                    }
                    return true;
                } else if (overseaUsim[i] == true) {
                    if (targetSim != -1) {
                        log("op01-C3: cur is old OS USIM but find op01 SIMs, change!");
                        setCapability(targetSim);
                    } else if (capabilitySimId != i) {
                        log("op01-C3a: old OS USIM change slot, change!");
                        setCapability(i);
                    }
                    return true;
                } else if (overseaSim[i] == true) {
                    if (targetSim != -1) {
                        log("op01-C4: cur is old OS SIM but find op01 SIMs/OS USIM, change!");
                        setCapability(targetSim);
                    } else if (capabilitySimId != i) {
                        log("op01-C4a: old OS SIM change slot, change!");
                        setCapability(i);
                    }
                    return true;
                } else if (targetSim != -1) {
                    log("op01-C5: cur is old non-op01 SIM/USIM but find higher SIM, change!");
                    setCapability(targetSim);
                    return true;
                }
                log("op01-C6: no higher priority SIM, no cahnge");
                return true;
            }
        }
        // cannot find default capability SIM, check if higher priority SIM exists
        targetSim = RadioCapabilitySwitchUtil.getHigherPrioritySimForOp01(capabilitySimId,
                op01Usim, op01Sim, overseaUsim, overseaSim);
        log("op01: target SIM :" + targetSim);
        if (op01Usim[capabilitySimId] == true) {
            log("op01-C7: cur is new op01 USIM, no change");
            return true;
        } else if (op01Sim[capabilitySimId] == true) {
            if (targetSim != -1) {
                log("op01-C8: cur is new op01 SIM but find op01 USIM, change!");
                setCapability(targetSim);
            }
            return true;
        } else if (overseaUsim[capabilitySimId] == true) {
            if (targetSim != -1) {
                log("op01-C9: cur is new OS USIM but find op01 SIMs, change!");
                setCapability(targetSim);
            }
            return true;
        } else if (overseaSim[capabilitySimId] == true) {
            if (targetSim != -1) {
                log("op01-C10: cur is new OS SIM but find op01 SIMs/OS USIM, change!");
                setCapability(targetSim);
            }
            return true;
        } else if (targetSim != -1) {
            log("op01-C11: cur is non-op01 but find higher priority SIM, change!");
            setCapability(targetSim);
        } else {
            log("op01-C12: no higher priority SIM, no cahnge");
        }
        return true;
    }

    private void subSelectorForOp09(Intent intent) {
        int phoneId = SubscriptionManager.INVALID_PHONE_INDEX;
        int insertedSimCount = 0;
        int insertedStatus = 0;
        int detectedType = intent.getIntExtra(SubscriptionManager.INTENT_KEY_DETECT_STATUS, 0);
        String[] currIccId = new String[mPhoneNum];

        log("DataSubSelector for op09");

        for (int i = 0; i < mPhoneNum; i++) {
            currIccId[i] = SystemProperties.get(PROPERTY_ICCID[i]);
            if (currIccId[i] == null || "".equals(currIccId[i])) {
                log("error: iccid not found, wait for next sub ready");
                return;
            }
            log("currIccId[" + i + "] : " + currIccId[i]);
            if (!NO_SIM_VALUE.equals(currIccId[i])) {
                ++insertedSimCount;
                insertedStatus = insertedStatus | (1 << i);
            }
        }

        log("Inserted SIM count: " + insertedSimCount + ", insertedStatus: " + insertedStatus);

        //Get previous default data
        String defaultIccid = SystemProperties.get(PROPERTY_DEFAULT_DATA_ICCID);
        log("Default data Iccid = " + defaultIccid);

        if (insertedSimCount == 0) {
            // No SIM inserted
            // 1. Default Data: unset
            // 2. Data Enable: OFF
            // 3. 34G: No change
            log("OP09 C0: No SIM inserted, set data unset");
            setDefaultData(SubscriptionManager.INVALID_PHONE_INDEX);
            for (int i = 0; i < mPhoneNum; i++) {
                setDataEnabled(i, false);
            }
        } else if (insertedSimCount == 1) {
            for (int i = 0; i < mPhoneNum; i++) {
                if ((insertedStatus & (1 << i)) != 0) {
                    phoneId = i;
                    break;
                }
            }

            if (detectedType == SubscriptionManager.EXTRA_VALUE_NOCHANGE) {
                log("OP09 C1: Single SIM unchange: do nothing");
            } else if (detectedType == SubscriptionManager.EXTRA_VALUE_NEW_SIM) {
                // Case 2: Single SIM + New SIM:
                // 1. Default Data: this sub
                // 2. Data Enable: ON
                log("OP09 C2: Single SIM + New SIM: Set Default data to phone:" + phoneId);
                setDefaultData(phoneId);
                setDataEnabled(phoneId, true);
            } else {
                // Case 3: Single SIM + old SIM:
                // 1. Default Data: this sub
                // 2. Data Enable: No change
                setDefaultData(phoneId);

                log("OP09 C3: Single SIM + Old SIM: check data enable");

                //TODO: Fix
                // Set data enabled for phoneId if the data of the other phone is enabled orginally
                String strEnabled = "0";
                if (phoneId == PhoneConstants.SIM_ID_1) {
                    strEnabled = TelephonyManager.getDefault().getTelephonyProperty(
                            PhoneConstants.SIM_ID_2, PROPERTY_MOBILE_DATA_ENABLE, "0");
                } else {
                    strEnabled = TelephonyManager.getDefault().getTelephonyProperty(
                            PhoneConstants.SIM_ID_1, PROPERTY_MOBILE_DATA_ENABLE, "0");
                }
                if (!strEnabled.equals("0") && strEnabled.length() > 1) {
                    setDataEnabled(phoneId, true);
                }
            }
        } else if (insertedSimCount >= 2) {
            if (detectedType == SubscriptionManager.EXTRA_VALUE_NEW_SIM) {
                int newSimStatus = intent.getIntExtra(
                        SubscriptionManager.INTENT_KEY_NEW_SIM_SLOT, 0);

                boolean isAllNewSim = true;
                for (int i = 0; i < mPhoneNum; i++) {
                    if ((newSimStatus & (1 << i)) == 0) {
                        isAllNewSim = false;
                    }
                }

                if (isAllNewSim) {
                    // Case 4: Multi SIM + All New SIM:
                    // 1. Default Data: SIM1
                    // 2. Data Enable: SIM1 ON / others off
                    // 3. 34G: Sub1
                    log("C4: Multi SIM + All New SIM: Set 34G to sub1");
                    setDefaultData(PhoneConstants.SIM_ID_1);
                    setDataEnabled(PhoneConstants.SIM_ID_1, true);
                    //TODO: Other SIMs
                } else {
                    if (defaultIccid == null || "".equals(defaultIccid)) {
                        //Not found previous default SIM, don't change.
                        // Case 6: Multi SIM + New SIM + Non Default SIM:
                        // 1. Default Data: SIM1
                        // 2. Data Enable: SIM1 no change / SIM2 OFF
                        log("C6: Multi SIM + New SIM + Non Default SIM: No Change");
                        setDefaultData(PhoneConstants.SIM_ID_1);
                        //TODO: Other SIMs
                        setDataEnabled(PhoneConstants.SIM_ID_2, false);
                    } else {
                        for (int i = 0; i < mPhoneNum; i++) {
                            if (defaultIccid.equals(currIccId[i])) {
                                phoneId = i;
                                break;
                            }
                        }

                        if (phoneId != SubscriptionManager.INVALID_PHONE_INDEX) {
                            // Case 5: Multi SIM + New SIM + Default SIM:
                            // 1. Default Data: Default SIM
                            // 2. Data Enable: Old SIM No Change / New SIM OFF
                            log("C5: Multi SIM + New SIM + Default SIM: Set Default data to "
                                + "phone:" + phoneId);

                            // Set the data status of non-default sim to false
                            int nonDefaultPhoneId = 0;
                            if (phoneId == 0) {
                                nonDefaultPhoneId = 1;
                            } else {
                                nonDefaultPhoneId = 0;
                            }
                            setDataEnabled(nonDefaultPhoneId, false);
                        } else {
                            // Case 9: Multi SIM + New SIM + Non Default SIM:
                            // 1. Default Data: SIM1
                            // 2. Data Enable: SIM1 no change / SIM2 OFF
                            int isLastDataEnabled = getLastDataEnabled();
                            log("C9: Multi SIM + New SIM + isLastDataEnabled = "
                                    + isLastDataEnabled);
                            setDefaultData(PhoneConstants.SIM_ID_1);
                            setDataEnabled(PhoneConstants.SIM_ID_1,
                                    (isLastDataEnabled == 0) ? false : true);
                            //TODO: Other SIMs
                            setDataEnabled(PhoneConstants.SIM_ID_2, false);
                        }
                    }
                }
            } else {
                if (defaultIccid == null || "".equals(defaultIccid)) {
                    //Case 8: Multi SIM + All Old SIM + No Default SIM:
                    //Do nothing
                    loge("C8: Do nothing");
                } else {
                    for (int i = 0; i < mPhoneNum; i++) {
                        if (defaultIccid.equals(currIccId[i])) {
                            phoneId = i;
                            break;
                        }
                    }
                    if (phoneId != SubscriptionManager.INVALID_PHONE_INDEX) {
                        // Case 7: Multi SIM + All Old SIM + Default SIM:
                        // 1. Default Data: Default SIM
                        // 2. Data Enable: No Change
                        log("C7: Multi SIM + All Old SIM + Default SIM: Set Default data to phone:"
                                + phoneId);

                        // Set the data status of non-default sim to false
                        int nonDefaultPhoneId = 0;
                        if (phoneId == 0) {
                            nonDefaultPhoneId = 1;
                        } else {
                            nonDefaultPhoneId = 0;
                        }
                        setDataEnabled(nonDefaultPhoneId, false);
                    } else {
                        //Case 8: Multi SIM + All Old SIM + No Default SIM:
                        //Do nothing
                        loge("C8: Do nothing");
                    }
                }
            }
        }
    }

    private void setDataEnabled(int phoneId, boolean enable) {
        log("setDataEnabled: phoneId=" + phoneId + ", enable=" + enable);

        TelephonyManager telephony = TelephonyManager.getDefault();
        if (telephony != null) {
            if (phoneId == SubscriptionManager.INVALID_PHONE_INDEX) {
                telephony.setDataEnabled(enable);
            } else {
                int phoneSubId = 0;
                if (enable == false) {
                    phoneSubId = PhoneFactory.getPhone(phoneId).getSubId();
                    log("Set Sub" + phoneSubId + " to disable");
                    telephony.setDataEnabled(phoneSubId, enable);
                } else {
                    for (int i = 0; i < mPhoneNum; i++) {
                        phoneSubId = PhoneFactory.getPhone(i).getSubId();
                        if (i != phoneId) {
                            log("Set Sub" + phoneSubId + " to disable");
                            telephony.setDataEnabled(phoneSubId, false);
                        } else {
                            log("Set Sub" + phoneSubId + " to enable");
                            telephony.setDataEnabled(phoneSubId, true);
                        }
                    }
                }
            }
        }
    }

    private void updateDataEnableProperty() {
        TelephonyManager telephony = TelephonyManager.getDefault();
        boolean dataEnabled = false;
        String dataOnIccid = "0";
        int subId = 0;

        for (int i = 0; i < mPhoneNum; i++) {
            // M: [C2K][IRAT] Change LTE_DC_SUB_ID to IRAT support slot sub ID for IRAT.
            subId = PhoneFactory.getPhone(i).getSubId();
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                subId = SvlteUtils.getSvlteSubIdBySubId(subId);
            }

            if (telephony != null) {
               dataEnabled = telephony.getDataEnabled(subId);
            }

            if (dataEnabled) {
                dataOnIccid = SystemProperties.get(PROPERTY_ICCID[i], "0");
            } else {
                dataOnIccid = "0";
            }
            

            log("setUserDataProperty:" + dataOnIccid);
            TelephonyManager.getDefault().setTelephonyProperty(i, PROPERTY_MOBILE_DATA_ENABLE,
                    dataOnIccid);
        }
    }

    private void subSelectorForSvlte(Intent intent) {
        int c2kP2 = Integer.valueOf(
                SystemProperties.get("ro.mtk.c2k.slot2.support", "0"));
        log("subSelectorForSvlte,c2kP2 = " + c2kP2);
        //For solution 1.5,only op09 project need limite capability
        if (OPERATOR_OP09.equals(mOperatorSpec)) {
           if (RadioCapabilitySwitchUtil.isSimContainsCdmaApp(PhoneConstants.SIM_ID_1)) {
              log("CDMA sim is inserted in slot1, always set to SIM1");
              setCapability(PhoneConstants.SIM_ID_1);
              return;
           }
           if (SvlteModeController.getRadioTechnologyMode() ==
                   SvlteModeController.RADIO_TECH_MODE_SVLTE) {
              // svlte mode
              // check sim 1 status
              int[] cardType = new int[TelephonyManager.getDefault().getPhoneCount()];
              cardType = UiccController.getInstance().getC2KWPCardType();
              log("card type: " + cardType[PhoneConstants.SIM_ID_1]);
              if (cardType[PhoneConstants.SIM_ID_1] == UiccController.CARD_TYPE_NONE) {
                  log("SIM 1 is empty, don't change capability");
              } else {
                  log("SIM 1 is inserted, change capability");
                  setCapability(PhoneConstants.SIM_ID_1);
              }
           } else {
              // csfb mode, follow om project switch
              subSelectorForOm(intent);
           }

        } else {
           // not op09 project, follow om project switch
           //For solution 1.5, G+C and C+G case default data and capability maybe not sync
           if ((c2kP2 == 0) && checkCdmaCardInsert()){
              subSelectorForSolution15(intent);
           } else {
              subSelectorForOm(intent);
           }
           
        }
    }

    private void setDefaultData(int phoneId) {
        SubscriptionController subController = SubscriptionController.getInstance();
        int sub = SubscriptionManager.getSubIdUsingPhoneId(phoneId);
        long currSub = SubscriptionManager.getDefaultDataSubId();

        log("setDefaultData: " + sub + ", current default sub:" + currSub);
        if (sub != currSub) {
            subController.setDefaultDataSubIdWithoutCapabilitySwitch(sub);
        } else {
            log("setDefaultData: default data unchanged");
        }
    }

    private void turnOffNewSimData(Intent intent) {
        int detectedType = intent.getIntExtra(SubscriptionManager.INTENT_KEY_DETECT_STATUS, 0);
        log("turnOffNewSimData detectedType = " + detectedType);

        //L MR1 Spec, turn off data if new sim inserted.
        if (detectedType == SubscriptionManager.EXTRA_VALUE_NEW_SIM) {
            int newSimSlot = intent.getIntExtra(
                    SubscriptionManager.INTENT_KEY_NEW_SIM_SLOT, 0);

            log("newSimSlot = " + newSimSlot);

            for (int i = 0; i < mPhoneNum; i++) {
                if ((newSimSlot & (1 << i)) != 0) {
                    String defaultIccid = SystemProperties.get(PROPERTY_DEFAULT_DATA_ICCID);
                    String newSimIccid = SystemProperties.get(PROPERTY_ICCID[i]);
                    if (!newSimIccid.equals(defaultIccid)) {
                        log("Detect NEW SIM, turn off phone " + i + " data.");
                        setDataEnabled(i, false);
                    }
                }
            }
        }
    }

    private boolean setCapability(int phoneId) {
        int[] phoneRat = new int[mPhoneNum];
        boolean isSwitchSuccess = true;

        log("setCapability: " + phoneId);

        String curr3GSim = SystemProperties.get(PROPERTY_3G_SIM, "");
        log("current 3G Sim = " + curr3GSim);

        if (curr3GSim != null && !curr3GSim.equals("")) {
            int curr3GPhoneId = Integer.parseInt(curr3GSim);
            if (curr3GPhoneId == (phoneId + 1) ) {
                log("Current 3G phone equals target phone, don't trigger switch");
                return isSwitchSuccess;
            }
        }

        try {
            ITelephony iTel = ITelephony.Stub.asInterface(
                    ServiceManager.getService(Context.TELEPHONY_SERVICE));
            ITelephonyEx iTelEx = ITelephonyEx.Stub.asInterface(
                    ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
            if (null == iTel) {
                loge("Can not get phone service");
                return false;
            }

            int currRat = iTel.getRadioAccessFamily(phoneId);
            log("Current phoneRat:" + currRat);

            RadioAccessFamily[] rat = new RadioAccessFamily[mPhoneNum];
            for (int i = 0; i < mPhoneNum; i++) {
                if (phoneId == i) {
                    log("SIM switch to Phone" + i);
                    phoneRat[i] = RadioAccessFamily.RAF_LTE
                            | RadioAccessFamily.RAF_UMTS
                            | RadioAccessFamily.RAF_GSM;
                } else {
                    phoneRat[i] = RadioAccessFamily.RAF_GSM;
                }
                rat[i] = new RadioAccessFamily(i, phoneRat[i]);
            }
            if (false  == iTelEx.setRadioCapability(rat)) {
                log("Set phone rat fail!!!");
                isSwitchSuccess = false;
            }
        } catch (RemoteException ex) {
            log("Set phone rat fail!!!");
            ex.printStackTrace();
            isSwitchSuccess = false;
        }

        return isSwitchSuccess;
    }

    private void subSelectorForOp09C(Intent intent) {
        int phoneId = SubscriptionManager.INVALID_PHONE_INDEX;
        int insertedSimCount = 0;
        int insertedStatus = 0;
        int detectedType = intent.getIntExtra(SubscriptionManager.INTENT_KEY_DETECT_STATUS, 0);
        String[] currIccId = new String[mPhoneNum];

        log("DataSubSelector for op09 C");

        for (int i = 0; i < mPhoneNum; i++) {
            currIccId[i] = SystemProperties.get(PROPERTY_ICCID[i]);
            if (currIccId[i] == null || "".equals(currIccId[i])) {
                log("error: iccid not found, wait for next sub ready");
                return;
            }
            log("currIccId[" + i + "] : " + currIccId[i]);
            if (!NO_SIM_VALUE.equals(currIccId[i])) {
                ++insertedSimCount;
                insertedStatus = insertedStatus | (1 << i);
            }
        }
        log("Inserted SIM count: " + insertedSimCount + ", insertedStatus: " + insertedStatus);

        //Get previous default data
        String defaultIccid = SystemProperties.get(PROPERTY_DEFAULT_DATA_ICCID);
        log("Default data Iccid = " + defaultIccid);

        if (insertedSimCount == 0) {
            log("OP09 C0: No SIM inserted, do nothing.");
        } else if (insertedSimCount == 1) {
            for (int i = 0; i < mPhoneNum; i++) {
                if ((insertedStatus & (1 << i)) != 0) {
                    phoneId = i;
                    break;
                }
            }

            if (detectedType == SubscriptionManager.EXTRA_VALUE_NOCHANGE) {
                log("OP09 C1: Single SIM unchange: do nothing");
            } else if (detectedType == SubscriptionManager.EXTRA_VALUE_NEW_SIM) {
                // Case 2: Single SIM + New SIM:
                // 1. Default Data: this sub
                // 2. Data Enable: ON
                log("OP09 C2: Single SIM + New SIM: Set Default data to phone:" + phoneId);
                if (setCapability(phoneId)) {
                    setDefaultData(phoneId);
                }
                setDataEnabled(phoneId, true);
            } else {
                // Case 3: Single SIM and it's old SIM:
                // 1. Default Data: this sub
                // 2. Data Enable: No change
                if (setCapability(phoneId)) {
                    setDefaultData(phoneId);
                }
                log("OP09 C3: Single SIM + Old SIM: set as default data.");
            }
        } else if (insertedSimCount >= 2) {
            if (detectedType == SubscriptionManager.EXTRA_VALUE_NEW_SIM) {
                int newSimStatus = intent.getIntExtra(
                        SubscriptionManager.INTENT_KEY_NEW_SIM_SLOT, 0);

                boolean isAllNewSim = true;
                for (int i = 0; i < mPhoneNum; i++) {
                    if ((newSimStatus & (1 << i)) == 0) {
                        isAllNewSim = false;
                    }
                }

                if (isAllNewSim) {
                    // Case 4: Multi SIM + All New SIM:
                    // 1. Default Data: SIM1
                    // 2. Data Enable: SIM1 ON / others off
                    // 3. 34G: Sub1
                    log("C4: Multi SIM + All New SIM: Set 34G to sub1");
                    if (setCapability(PhoneConstants.SIM_ID_1)) {
                        setDefaultData(PhoneConstants.SIM_ID_1);
                    }
                    setDataEnabled(PhoneConstants.SIM_ID_1, true);
                    setDataEnabled(PhoneConstants.SIM_ID_2, false);
                } else {
                    if (defaultIccid == null || "".equals(defaultIccid)) {
                        //Not found previous default SIM, don't change.
                        // Case 6: Multi SIM + New SIM + Non Default SIM:
                        // 1. Default Data: SIM1
                        // 2. Data Enable: SIM1 no change / SIM2 OFF
                        log("C6: Multi SIM + New SIM + Non Default SIM: No Change");
                        if (setCapability(PhoneConstants.SIM_ID_1)) {
                            setDefaultData(PhoneConstants.SIM_ID_1);
                        }
                        setDataEnabled(PhoneConstants.SIM_ID_1, true);
                        setDataEnabled(PhoneConstants.SIM_ID_2, false);
                    } else {
                        for (int i = 0; i < mPhoneNum; i++) {
                            if (defaultIccid.equals(currIccId[i])) {
                                phoneId = i;
                                break;
                            }
                        }

                        if (phoneId != SubscriptionManager.INVALID_PHONE_INDEX) {
                            // Case 5: Multi SIM + New SIM + Default SIM:
                            // 1. Default Data: Default SIM
                            // 2. Data Enable: Old SIM No Change / New SIM OFF
                            log("C5: Multi SIM + New SIM + Default SIM: Keep Set Default data to "
                                + "phone:" + phoneId);
                            // Set the data status of non-default sim to false
                            int nonDefaultPhoneId = 0;
                            if (phoneId == 0) {
                                nonDefaultPhoneId = 1;
                            } else {
                                nonDefaultPhoneId = 0;
                            }
                            setDataEnabled(nonDefaultPhoneId, false);
                        } else {
                            // Case 7: Multi SIM + New SIM + Non Default SIM:
                            // 1. Default Data: SIM1
                            // 2. Data Enable: SIM1 no change / SIM2 OFF
                            log("C7: Multi SIM + New SIM + Non Default SIM: set to sim1.");
                            if (setCapability(PhoneConstants.SIM_ID_1)) {
                                setDefaultData(PhoneConstants.SIM_ID_1);
                            }
                            setDataEnabled(PhoneConstants.SIM_ID_1, true);
                            setDataEnabled(PhoneConstants.SIM_ID_2, false);
                        }
                    }
                }
            } else {
                //Case 8: Multi SIM + All Old SIM.
                // 1. Default Data: Default SIM
                // 2. Follow OM rule
                loge("C8: Multi SIM + All Old SIM :set as previous default data");
                subSelectorForOm(intent);
            }
        }
    }

    private void log(String txt) {
        if (DBG) {
            Rlog.d("DataSubSelector", txt);
        }
    }

    private void loge(String txt) {
        if (DBG) {
            Rlog.e("DataSubSelector", txt);
        }
    }

    /**
     * Return enable, whether the data of the Last card is enabled.
     */
    private int getLastDataEnabled() {
        int subId = SubscriptionManager.getDefaultDataSubId();
        log("DataSubselector getLastDataEnable subId = " + subId);
        int enabled = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.MOBILE_DATA + subId, 0);
        return enabled;
    }
}
