/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mediatek.keyguard.Telephony;

import android.content.Context;
import android.content.res.TypedArray;
import android.net.ConnectivityManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log ;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardUpdateMonitor ;
import com.android.keyguard.KeyguardUpdateMonitorCallback ;
import com.android.keyguard.KeyguardUtils ;
import com.android.keyguard.R ;


import com.mediatek.keyguard.ext.KeyguardPluginFactory;
import com.mediatek.keyguard.ext.ICarrierTextExt;
import com.mediatek.keyguard.ext.IOperatorSIMString;
import com.mediatek.keyguard.ext.IOperatorSIMString.SIMChangedTag;

//import com.android.internal.R;

public class MediatekCarrierText extends LinearLayout {
    private static final String TAG = "MediatekCarrierText";
    private static final int MAX_CARRIER_TEXT_NUM = 4;
    private static CharSequence mSeparator;

    private Context mContext ;
    private LockPatternUtils mLockPatternUtils;

    private KeyguardUpdateMonitor mUpdateMonitor;
    private int mNumOfPhone;
    private TextView mCarrierView[];
    private TextView mCarrierDivider[];
    private State mSimState[];
    private StatusMode mStatusMode[];
    private boolean mUseAllCaps;

    /// M: To get the proper SIM UIM string according to operator.
    private IOperatorSIMString mIOperatorSIMString;
    /// M: To changed the plmn  CHINA TELECOM to China Telecom,just for CT feature
    private ICarrierTextExt mCarrierTextExt;
    /// M: added for CDMA card type is locked.
    private boolean mIsLockedCard = false;

    private KeyguardUpdateMonitorCallback mCallback = new KeyguardUpdateMonitorCallback() {
        /*@Override
        public void onRefreshCarrierInfo(int phoneId, CharSequence plmn, CharSequence spn) {
            Log.d(TAG, "onRefreshCarrierInfo(phoneId = " + phoneId + ")") ;
            updateCarrierText(phoneId, mUpdateMonitor.getSimStateOfPhoneId(phoneId), plmn, spn);
        }*/

        @Override
        public void onSimStateChangedUsingPhoneId(int phoneId, IccCardConstants.State simState) {
            Log.d(TAG, "onSimStateChangedUsingPhoneId(phoneId = " + phoneId + ")") ;
            updateCarrierText(phoneId, simState, mUpdateMonitor.getTelephonyPlmn(phoneId),
                    mUpdateMonitor.getTelephonySpn(phoneId));
        }

        @Override
        public void onScreenTurnedOff(int why) {
            for (int i = 0; i < mNumOfPhone; i++) {
                mCarrierView[i].setSelected(false);
            }
        };

        @Override
        public void onScreenTurnedOn() {
            for (int i = 0; i < mNumOfPhone; i++) {
                mCarrierView[i].setSelected(true);
            }
        };

        /**
         * M: added for CDMA card type is locked.
         * Handler of CDMA card type changed.
         *
         * @param isLockedCard whether the card is locked.
         */
        @Override
        public void onCDMACardTypeChanges(boolean isLockedCard) {
            Log.d(TAG, "onCDMACardTypeChanges(isLockedCard = " + isLockedCard + ")") ;
            mIsLockedCard = isLockedCard;
            int phoneId = PhoneConstants.SIM_ID_1;
            updateCarrierText(phoneId, mUpdateMonitor.getSimStateOfPhoneId(phoneId),
                    mUpdateMonitor.getTelephonyPlmn(phoneId),
                    mUpdateMonitor.getTelephonySpn(phoneId));
        };
    };

    /**
     * The status of this lock screen. Primarily used for widgets on LockScreen.
     */
    private static enum StatusMode {
        Normal, // Normal case (sim card present, it's not locked)
        NetworkLocked, // SIM card is 'network locked'.
        SimMissing, // SIM card is missing.
        SimMissingLocked, // SIM card is missing, and device isn't provisioned; don't allow access
        SimPukLocked, // SIM card is PUK locked because SIM entered wrong too many times
        SimLocked, // SIM card is currently locked
        SimPermDisabled, // SIM card is permanently disabled due to PUK unlock failure
        SimNotReady, // SIM is not ready yet. May never be on devices w/o a SIM.

        /// M: mediatek add sim state
        SimUnknown,
        NetworkSearching;  //The sim card is ready, but searching network
    }

    /// M: Support GeminiPlus
    private void initMembers() {
        mNumOfPhone = KeyguardUtils.getNumOfPhone();

        mCarrierView = new TextView[4];
        mCarrierDivider = new TextView[3];
        mStatusMode = new StatusMode[mNumOfPhone];
        for (int i = 0; i < mNumOfPhone ; i++) {
            mStatusMode[i] = StatusMode.Normal;
        }
    }

    public MediatekCarrierText(Context context) {
        this(context, null);
        initMembers();
    }

    public MediatekCarrierText(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context ;
        mLockPatternUtils = new LockPatternUtils(mContext);
        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(mContext);

        initMembers();

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.keyguard_carrier_text_view, this, true);

        /// M: Init the plugin for changing the String with SIM according to Operator.
        mIOperatorSIMString = KeyguardPluginFactory.getOperatorSIMString(mContext);
        mCarrierTextExt = KeyguardPluginFactory.getCarrierTextExt(mContext);

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.CarrierText, 0, 0);
        try {
            mUseAllCaps = a.getBoolean(R.styleable.CarrierText_allCaps, false);
        } finally {
            a.recycle();
        }
    }

    /**
     * Set all carrier texts new text size.
     * @param unit The desired dimension unit.
     * @param size The desired size in the given units.
     */
    public void setTextSize(int unit, float size) {
        for (int i = 0; i < mNumOfPhone; i++) {
            mCarrierView[i].setTextSize(unit, size);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSeparator = getResources().getString(
            com.android.internal.R.string.kg_text_message_separator);
        final boolean screenOn = mUpdateMonitor.isScreenOn();
        setLayerType(LAYER_TYPE_HARDWARE, null); // work around nested unclipped SaveLayer bug

        mCarrierView[0] = (TextView) findViewById(R.id.carrier_1);
        mCarrierView[1] = (TextView) findViewById(R.id.carrier_2);
        mCarrierView[2] = (TextView) findViewById(R.id.carrier_3);
        mCarrierView[3] = (TextView) findViewById(R.id.carrier_4);
        mCarrierDivider[0] = (TextView) findViewById(R.id.carrier_divider_1);
        mCarrierDivider[1] = (TextView) findViewById(R.id.carrier_divider_2);
        mCarrierDivider[2] = (TextView) findViewById(R.id.carrier_divider_3);
        mCarrierView[0].setSelected(screenOn);
        mCarrierView[1].setSelected(screenOn);
        mCarrierView[2].setSelected(screenOn);
        mCarrierView[3].setSelected(screenOn);

        for (int i = 0; i < mNumOfPhone; i++) {
            mCarrierView[i].setVisibility(View.VISIBLE);
            if (i < mNumOfPhone - 1) {
                mCarrierDivider[i].setVisibility(View.VISIBLE);
                mCarrierDivider[i].setText("|");
            }

        }

        if (mNumOfPhone == 2) {
            mCarrierView[0].setGravity(Gravity.END);
            mCarrierView[1].setGravity(Gravity.START);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mUpdateMonitor.registerCallback(mCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mUpdateMonitor.removeCallback(mCallback);
    }

    /**
     * Top-level function for creating carrier text. Makes text based on simState, PLMN
     * and SPN as well as device capabilities, such as being emergency call capable.
     *
     * @param simState
     * @param plmn
     * @param spn
     * @param hnbName
     * @param csgid
     * @return
     */
    private CharSequence getCarrierTextForSimState(int phoneId, IccCardConstants.State simState,
            CharSequence plmn, CharSequence spn, CharSequence hnbName, CharSequence csgId) {

        /// M: Onle set plmn to default value if both plmn and spn are null
        if (plmn == null && spn == null) {
            plmn = mUpdateMonitor.getDefaultPlmn();
        }

        CharSequence carrierText = null;
        StatusMode status = getStatusForIccState(simState);
        switch (status) {
            case SimUnknown:
            case Normal:
                carrierText = concatenate(plmn, spn);
                carrierText = appendCsgInfo(carrierText, hnbName, csgId);
                break;

            case SimNotReady:
                carrierText = mUpdateMonitor.getDefaultPlmn();
                break;

            case NetworkLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        mContext.getText(R.string.keyguard_network_locked_message),
                        plmn, hnbName, csgId);
                break;

            case SimMissing:
                // Shows "No SIM card | Emergency calls only" on devices that are voice-capable.
                // This depends on mPlmn containing the text "Emergency calls only" when the radio
                // has some connectivity. Otherwise, it should be null or empty and just show
                // "No SIM card"
                CharSequence simMessage = getContext().getText(
                                            R.string.keyguard_missing_sim_message_short);
                carrierText = makeCarrierStringOnEmergencyCapable(simMessage, plmn, hnbName, csgId);
                carrierText = mCarrierTextExt.customizeCarrierText(carrierText,
                                                                   simMessage, phoneId);
                break;

            case SimPermDisabled:
                carrierText = getContext().getText(R.string.keyguard_permanent_disabled_sim_message_short);
                break;

            case SimMissingLocked:
                carrierText =  makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_missing_sim_message_short),
                        plmn, hnbName, csgId);
                break;

            case SimLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_sim_locked_message),
                        plmn, hnbName, csgId);
                break;

            case SimPukLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_sim_puk_locked_message),
                        plmn, hnbName, csgId);
                break;
            default:
                carrierText = plmn;
                break;
        }

        /// M: added for CDMA card type is locked.
        carrierText = mCarrierTextExt.customizeCarrierTextWhenCardTypeLocked(
                                carrierText, mContext, phoneId, mIsLockedCard).toString();
        Log.d(TAG, "getCarrierTextForSimState phoneId=" + phoneId + " simState=" + simState +
            " PLMN=" + plmn + " SPN=" + spn + " HNB=" + hnbName + " CSG=" + csgId + " carrierText=" + carrierText);
        return carrierText;
    }

    /*
     * Add emergencyCallMessage to carrier string only if phone supports emergency calls.
     */
    private CharSequence makeCarrierStringOnEmergencyCapable(
            CharSequence simMessage, CharSequence emergencyCallMessage,
            CharSequence hnbName, CharSequence csgId) {

        CharSequence emergencyCallMessageExtend = emergencyCallMessage;
        if (!TextUtils.isEmpty(emergencyCallMessage)) {
            emergencyCallMessageExtend = appendCsgInfo(emergencyCallMessage, hnbName, csgId);
        }

        if (mLockPatternUtils.isEmergencyCallCapable()) {
            return concatenate(simMessage, emergencyCallMessageExtend);
        }
        return simMessage;
    }

    /**
     * Determine the current status of the lock screen given the SIM state and other stuff.
     */
    private StatusMode getStatusForIccState(IccCardConstants.State simState) {
        // Since reading the SIM may take a while, we assume it is present until told otherwise.
        if (simState == null) {
            return StatusMode.SimUnknown;
        }

        final boolean missingAndNotProvisioned =
                !KeyguardUpdateMonitor.getInstance(mContext).isDeviceProvisioned()
                && (simState == IccCardConstants.State.ABSENT ||
                        simState == IccCardConstants.State.PERM_DISABLED);

        // M: Directly maps missing and not Provisioned to SimMissingLocked Status.
        if (missingAndNotProvisioned) {
            return StatusMode.SimMissingLocked;
        }
        //simState = missingAndNotProvisioned ? IccCardConstants.State.NETWORK_LOCKED : simState;
        //@}

        switch (simState) {
            case ABSENT:
                return StatusMode.SimMissing;
            case NETWORK_LOCKED:
                // M: correct IccCard state NETWORK_LOCKED maps to NetowrkLocked.
                return StatusMode.NetworkLocked;
            case NOT_READY:
                return StatusMode.SimNotReady;
            case PIN_REQUIRED:
                return StatusMode.SimLocked;
            case PUK_REQUIRED:
                return StatusMode.SimPukLocked;
            case READY:
                return StatusMode.Normal;
            case PERM_DISABLED:
                return StatusMode.SimPermDisabled;
            case UNKNOWN:
                return StatusMode.SimUnknown;
        }
        return StatusMode.SimMissing;
    }

    private static CharSequence concatenate(CharSequence str1, CharSequence str2) {
        final boolean str1Valid = !TextUtils.isEmpty(str1);
        final boolean str2Valid = !TextUtils.isEmpty(str2);
        if (str1Valid && str2Valid) {
            if (str1.equals(str2)) {
                return str1;
            } else {
                return new StringBuilder().append(str1).append(mSeparator).append(str2).toString();
            }
        } else if (str1Valid) {
            return str1;
        } else if (str2Valid) {
            return str2;
        } else {
            return "";
        }
    }


    protected void updateCarrierText(int phoneId, State simState, CharSequence plmn,
                                     CharSequence spn) {
        Log.d(TAG, "updateCarrierText, simState=" + simState + " plmn=" + plmn +
                   " spn=" + spn + " phoneId=" + phoneId);
        TextView toSetCarrierView;

        if (!KeyguardUtils.isValidPhoneId(phoneId)) {
            Log.d(TAG, "updateCarrierText, invalidate phoneId=" + phoneId);
            return;
        }

        toSetCarrierView = mCarrierView[phoneId];
        //if (StatusMode.NetworkSearching == mStatusMode[simId]) {
        //    Log.d(TAG, "updateCarrierText, searching network now, don't interrupt it, simState=" + simState);
        //    return;
        //}
        /// M: save statu mode, which will be used to decide show or hide carrier view
        mStatusMode[phoneId] = getStatusForIccState(simState);

        if (isWifiOnlyDevice()) {
            Log.d(TAG, "updateCarrierText WifiOnly");
            mCarrierView[0].setVisibility(View.GONE);
            return;
        }

        showOrHideCarrier();

        CharSequence text = getCarrierTextForSimState(phoneId, simState, plmn, spn,
        mUpdateMonitor.getTelephonyHnbNameOfPhoneId(phoneId),
        mUpdateMonitor.getTelephonyCsgIdOfPhoneId(phoneId));
        /// M: Change the String with SIM according to Operator.
        if (text != null) {
            text = mIOperatorSIMString.getOperatorSIMString(text.toString(), phoneId,
                                                            SIMChangedTag.DELSIM, mContext);
        }

        toSetCarrierView.setText(text != null ? mCarrierTextExt.customizeCarrierTextCapital(text.toString()) : null);
    }

    /**
     * M: Used to check weather this device is wifi only.
     */
    private boolean isWifiOnlyDevice() {
        ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        return  !(cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE));
    }

    /**
     * M: Used to control carrier TextView visibility in Gemini.
     * (1) if the device is wifi only, we hide both carrier TextView.
     * (2) if both sim are missing, we shwon only one carrier TextView center.
     * (3) if either one sim is missing, we shwon the visible carrier TextView center.
     * (4) if both sim are not missing, we shwon boteh TextView, one in the left the other right.
     */
    /// M: Support GeminiPlus
    private void showOrHideCarrier() {
        if (isWifiOnlyDevice()) {
            for (int i = 0; i < mNumOfPhone; i++) {
                if (mCarrierView[i] != null) {
                    mCarrierView[i].setVisibility(View.GONE);
                }
            }

            for (int i = 0; i < mNumOfPhone - 1; i++) {
                if (mCarrierDivider[i] != null) {
                    mCarrierDivider[i].setVisibility(View.GONE);
                }
            }
        } else {
            int mNumOfSIM = 0;
            TextView mCarrierLeft = null;
            TextView mCarrierRight = null;

            for (int i = 0; i < mNumOfPhone - 1; i++) {
                if (mCarrierDivider[i] != null) {
                    mCarrierDivider[i].setVisibility(View.GONE);
                }
            }

            for (int i = 0; i < mNumOfPhone; i++) {
                boolean simMissing = (mStatusMode[i] == StatusMode.SimMissing
                    || mStatusMode[i] == StatusMode.SimMissingLocked
                    || mStatusMode[i] == StatusMode.SimUnknown);
                simMissing = mCarrierTextExt.showCarrierTextWhenSimMissing(simMissing, i);

                if (!simMissing) {
                    if (mCarrierView[i] != null) {
                        mCarrierView[i].setVisibility(View.VISIBLE);
                    }
                    mNumOfSIM++;
                    if (mNumOfSIM == 1) {
                        mCarrierLeft = mCarrierView[i];
                    } else if (mNumOfSIM == 2) {
                        mCarrierRight = mCarrierView[i];
                    }
                    if (mNumOfSIM >= 2 && ((i - 1) >= 0) && (mCarrierDivider != null)) {
                        mCarrierDivider[i - 1].setVisibility(View.VISIBLE);
                        mCarrierDivider[i - 1].setText("|");
                    }
                } else {
                    if (mCarrierView[i] != null) {
                        mCarrierView[i].setVisibility(View.GONE);
                    }
                }
                if (mCarrierView[i] != null) {
                    mCarrierView[i].setGravity(Gravity.CENTER);
                }
            }

            if (mNumOfSIM == 2) {
                if (mCarrierLeft != null) {
                    mCarrierLeft.setGravity(Gravity.END);
                }
                if (mCarrierRight != null) {
                    mCarrierRight.setGravity(Gravity.START);
                }
            } else if (mNumOfSIM == 0) {
                String defaultPlmn = mUpdateMonitor.getDefaultPlmn().toString();
                int index = 0;
                for (int i = 0; i < mNumOfPhone; i++) {
                    CharSequence plmn = mUpdateMonitor.getTelephonyPlmn(i);
                    if (plmn != null && defaultPlmn.contentEquals(plmn) == false) {
                        index = i;
                        break;
                    }
                }
                if (mCarrierView[index] != null) {
                    mCarrierView[index].setVisibility(View.VISIBLE);
                }
                Log.d(TAG, "updateOperatorInfo, No SIM cards, force slotId " + index + " to visible.");
            }
        }
    }

    /*private void updateCarrierTextForSearchNetwork(String carrierText, int simId) {
        Log.d(TAG, "updateCarrierTextForSearchNetwork carrierText=" + carrierText + ", simId=" + simId);
        if (isWifiOnlyDevice()) {
            Log.d(TAG, "updateCarrierTextForSearchNetwork WifiOnly");
            mCarrierView[PhoneConstants.SIM_ID_1].setVisibility(View.GONE);
        } else {
            mCarrierView[simId].setText(carrierText);
            showOrHideCarrier();
        }
    }*/

    private CharSequence appendCsgInfo(CharSequence srcText, CharSequence hnbName, CharSequence csgId) {
        CharSequence outText = srcText;
        if (!TextUtils.isEmpty(hnbName)) {
            outText = concatenate(srcText, hnbName);
        } else if (!TextUtils.isEmpty(csgId)) {
            outText = concatenate(srcText, csgId);
        }

        return outText;
    }
}
