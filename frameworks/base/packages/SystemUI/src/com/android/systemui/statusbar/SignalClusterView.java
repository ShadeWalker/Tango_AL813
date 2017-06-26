/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.systemui.statusbar;

import android.content.Context;
import android.telephony.SubscriptionInfo;
/// M: Add for CT6M, add data activity icon. @{
import android.telephony.TelephonyManager;
/// @}
import android.util.AttributeSet;
import android.util.Log;
/// M: Add for CT6M, add data activity icon. @{
import android.view.Gravity;
/// @}
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.NetworkControllerImpl;
import com.android.systemui.statusbar.policy.SecurityController;
import com.android.systemui.statusbar.policy.TelephonyIcons;
import com.mediatek.systemui.ext.FeatureOptionUtils;
import com.mediatek.systemui.ext.ISignalClusterExt;
import com.mediatek.systemui.ext.NetworkType;
import com.mediatek.systemui.ext.PhoneStateExt;
import com.mediatek.systemui.ext.PluginFactory;
import com.mediatek.systemui.statusbar.util.SIMHelper;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;
import java.util.List;

// Intimately tied to the design of res/layout/signal_cluster_view.xml
public class SignalClusterView
        extends LinearLayout
        implements NetworkControllerImpl.SignalCluster,
        SecurityController.SecurityControllerCallback {

    static final String TAG = "SignalClusterView";
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    NetworkControllerImpl mNC;
    SecurityController mSC;

    private boolean mNoSimsVisible = false;
    private boolean mVpnVisible = false;
    private boolean mWifiVisible = false;
    private int mWifiStrengthId = 0;
    private boolean mIsAirplaneMode = false;
    private int mAirplaneIconId = 0;
    private int mAirplaneContentDescription;
    private String mWifiDescription;
    private ArrayList<PhoneState> mPhoneStates = new ArrayList<PhoneState>();

    ViewGroup mWifiGroup;
    ImageView mVpn, mWifi, mAirplane, mNoSims;
    View mWifiAirplaneSpacer;
    View mWifiSignalSpacer;
    LinearLayout mMobileSignalGroup;

    private int mWideTypeIconStartPadding;
    private int mSecondaryTelephonyPadding;
    private int mEndPadding;
    private int mEndPaddingNothingVisible;

    /// M: Support "SystemUI - VoLTE icon".
    private ImageView mVoLTEIcon;
    /// M: Support "SIM Indicator".
    private ImageView mSimIndicatorIcon;

    /// M: Support "Operator plugin's ISignalClusterExt".@{
    private ISignalClusterExt mSignalClusterExt = null;
    /// M: Support "Operator plugin's ISignalClusterExt". @}

    public SignalClusterView(Context context) {
        this(context, null);
    }

    public SignalClusterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalClusterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        /// M: Support "Operator plugin's ISignalClusterExt".@{
        mSignalClusterExt = PluginFactory.getStatusBarPlugin(this.getContext())
                .customizeSignalCluster();
        mSignalClusterExt.setSignalClusterInfo(new SignalClusterInfo());
        /// M: Support "Operator plugin's ISignalClusterExt". @}
    }

    public void setNetworkController(NetworkControllerImpl nc) {
        if (DEBUG) Log.d(TAG, "NetworkController=" + nc);
        mNC = nc;

        /// M: Support "Operator plugin's ISignalClusterExt".@{
        mSignalClusterExt.setNetworkControllerExt(nc.getNetworkControllerExt());
        /// M: Support "Operator plugin's ISignalClusterExt". @}
    }

    public void setSecurityController(SecurityController sc) {
        if (DEBUG) Log.d(TAG, "SecurityController=" + sc);
        mSC = sc;
        mSC.addCallback(this);
        mVpnVisible = mSC.isVpnEnabled();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mWideTypeIconStartPadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.wide_type_icon_start_padding);
        mSecondaryTelephonyPadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.secondary_telephony_padding);
        mEndPadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.signal_cluster_battery_padding);
        mEndPaddingNothingVisible = getContext().getResources().getDimensionPixelSize(
                R.dimen.no_signal_cluster_battery_padding);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mVpn            = (ImageView) findViewById(R.id.vpn);
        mWifiGroup      = (ViewGroup) findViewById(R.id.wifi_combo);
        mWifi           = (ImageView) findViewById(R.id.wifi_signal);
        mAirplane       = (ImageView) findViewById(R.id.airplane);
        mNoSims         = (ImageView) findViewById(R.id.no_sims);
        mWifiAirplaneSpacer =         findViewById(R.id.wifi_airplane_spacer);
        mWifiSignalSpacer =           findViewById(R.id.wifi_signal_spacer);
        mMobileSignalGroup = (LinearLayout) findViewById(R.id.mobile_signal_group);
        for (PhoneState state : mPhoneStates) {
            mMobileSignalGroup.addView(state.mMobileGroup);
        }

        /// M: Support "SystemUI - VoLTE icon". @{
        mVoLTEIcon      = (ImageView) findViewById(R.id.volte_icon);
        mVoLTEIcon.setVisibility(View.GONE);
        /// M: Support "SystemUI - VoLTE icon". @}

        /// M: [SystemUI] Support "SIM indicator".
        mSimIndicatorIcon = (ImageView) findViewById(R.id.sim_indicator_internet_or_alwaysask);

        /// M: Support "Operator plugin's ISignalClusterExt".@{
        mSignalClusterExt.onAttachedToWindow(mMobileSignalGroup, mNoSims);
        /// M: Support "Operator plugin's ISignalClusterExt". @}

        apply();
    }

    @Override
    protected void onDetachedFromWindow() {
        mVpn            = null;
        mWifiGroup      = null;
        mWifi           = null;
        mAirplane       = null;
        mMobileSignalGroup.removeAllViews();
        mMobileSignalGroup = null;

        /// M: Support "Operator plugin's ISignalClusterExt".@{
        mSignalClusterExt.onDetachedFromWindow();
        /// M: Support "Operator plugin's ISignalClusterExt". @}

        super.onDetachedFromWindow();
    }

    // From SecurityController.
    @Override
    public void onStateChanged() {
        post(new Runnable() {
            @Override
            public void run() {
                mVpnVisible = mSC.isVpnEnabled();
                apply();
            }
        });
    }

    @Override
    public void setWifiIndicators(boolean visible, int strengthIcon, String contentDescription) {
        mWifiVisible = visible;
        mWifiStrengthId = strengthIcon;
        mWifiDescription = contentDescription;
        Xlog.d(TAG, "setWifiIndicators, visible=" + visible
            + ", strengthIcon=" + strengthIcon
            + ", contentDescription=" + contentDescription);

        apply();
    }

    @Override
    public void setMobileDataIndicators(boolean visible, int strengthIcon, int typeIcon,
            /// M: Add for CT6M. add activity icon @{
            int dataActivity,
            int primarySimIcon,
            /// @}
            String contentDescription, String typeContentDescription, boolean isTypeIconWide,
            int subId) {
        /// M: "Add getState". @{
        PhoneState state = getState(subId);
        if (state == null) {
            Log.d(TAG, "setMobileDataIndicators(" + subId + "), subId = " +
            subId + " is not exist");
            return;
        }
        /// M: "Add getState". @}

        /// M: Add for CT6M. add activity icon @{
        state.mDataActivity = dataActivity;
        state.mPrimarySimIconId = primarySimIcon;
        /// @}
        state.mMobileVisible = visible;
        state.mMobileStrengthId = strengthIcon;
        state.mMobileTypeId = typeIcon;
        state.mMobileDescription = contentDescription;
        state.mMobileTypeDescription = typeContentDescription;
        state.mIsMobileTypeIconWide = isTypeIconWide;
        Xlog.d(TAG, "setMobileDataIndicators(" + subId + "), visible=" + visible
            + ", strengthIcon= " + strengthIcon
            + ", mMobileTypeId= " + typeIcon
            + ", isTypeIconWide= " + isTypeIconWide);

        /// M: Support "Operator plugin's ISignalClusterExt". @{
        mSignalClusterExt.setMobileDataIndicators(subId, visible, state.mMobileGroup,
                state.mSignalNetworkType, (ViewGroup) (state.mMobile.getParent()), state.mMobile,
                state.mMobileType, strengthIcon, typeIcon, contentDescription,
                typeContentDescription, isTypeIconWide);
        /// M: Support "Operator plugin's ISignalClusterExt". @}

        apply();
    }

    @Override
    public void setNoSims(boolean show) {
        mNoSimsVisible = show;
        Log.d(TAG, "setNoSims(), mNoSimsVisible= " + mNoSimsVisible);

        /// M: Support "Operator plugin's ISignalClusterExt". @{
        mNoSimsVisible = PluginFactory.getStatusBarPlugin(mContext).customizeHasNoSims(show);
        /// M: Support "Operator plugin's ISignalClusterExt". @}
    }

    @Override
    public void setSubs(List<SubscriptionInfo> subs) {
        Xlog.d(TAG, "setSubs(), subs= " + subs);
        // Clear out all old subIds.
        mPhoneStates.clear();
        if (mMobileSignalGroup != null) {
            mMobileSignalGroup.removeAllViews();
        }
        final int n = subs.size();
        for (int i = 0; i < n; i++) {
            inflatePhoneState(subs.get(i).getSubscriptionId());
        }

        /// M: Support "Operator plugin's ISignalClusterExt". @{
        mSignalClusterExt.setSubs(subs, inflatePhoneStateExt(subs));
        /// M: Support "Operator plugin's ISignalClusterExt". @}
    }

    private PhoneState getOrInflateState(int subId) {
        for (PhoneState state : mPhoneStates) {
            if (state.mSubId == subId) {
                return state;
            }
        }
        return inflatePhoneState(subId);
    }

    /// M: "Add getState". @{
    private PhoneState getState(int subId) {
        for (PhoneState state : mPhoneStates) {
            if (state.mSubId == subId) {
                return state;
            }
        }
        return null;
    }
    /// M: "Add getState". @}

    private PhoneState inflatePhoneState(int subId) {
        PhoneState state = new PhoneState(subId, mContext);
        if (mMobileSignalGroup != null) {
            mMobileSignalGroup.addView(state.mMobileGroup);
        }
        mPhoneStates.add(state);
        return state;
    }

    @Override
    public void setIsAirplaneMode(boolean is, int airplaneIconId, int contentDescription) {
        mIsAirplaneMode = is;
        mAirplaneIconId = airplaneIconId;
        mAirplaneContentDescription = contentDescription;

        apply();
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // Standard group layout onPopulateAccessibilityEvent() implementations
        // ignore content description, so populate manually
        if (mWifiVisible && mWifiGroup != null && mWifiGroup.getContentDescription() != null)
            event.getText().add(mWifiGroup.getContentDescription());
        for (PhoneState state : mPhoneStates) {
            state.populateAccessibilityEvent(event);
        }
        return super.dispatchPopulateAccessibilityEvent(event);
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);

        if (mWifi != null) {
            mWifi.setImageDrawable(null);
        }

        for (PhoneState state : mPhoneStates) {
            if (state.mMobile != null) {
                state.mMobile.setImageDrawable(null);
            }
            if (state.mMobileType != null) {
                state.mMobileType.setImageDrawable(null);
            }
        }

        if(mAirplane != null) {
            mAirplane.setImageDrawable(null);
        }

        /// M: Support "Operator plugin's ISignalClusterExt". @{
        mSignalClusterExt.onRtlPropertiesChanged(layoutDirection);
        /// M: Support "Operator plugin's ISignalClusterExt". @}

        apply();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    // Run after each indicator change.
    private void apply() {
        if (mWifiGroup == null) return;

        mVpn.setVisibility(mVpnVisible ? View.VISIBLE : View.GONE);
        if (DEBUG) Log.d(TAG, String.format("vpn: %s", mVpnVisible ? "VISIBLE" : "GONE"));
        if (mWifiVisible) {
            mWifi.setImageResource(mWifiStrengthId);
            mWifiGroup.setContentDescription(mWifiDescription);
            mWifiGroup.setVisibility(View.VISIBLE);
        } else {
            mWifiGroup.setVisibility(View.GONE);
        }

        if (DEBUG) Log.d(TAG,
                String.format("wifi: %s sig=%d",
                    (mWifiVisible ? "VISIBLE" : "GONE"),
                    mWifiStrengthId));

        boolean anyMobileVisible = false;
        int firstMobileTypeId = 0;
        Log.d(TAG, "apply() PhoneState state : " + mPhoneStates);
        for (PhoneState state : mPhoneStates) {
            if (state.apply(anyMobileVisible)) {
                if (!anyMobileVisible) {
                    firstMobileTypeId = state.mMobileTypeId;
                    anyMobileVisible = true;
                }
            }
        }

        if (mIsAirplaneMode) {
            mAirplane.setImageResource(mAirplaneIconId);
            mAirplane.setContentDescription(mAirplaneContentDescription != 0 ?
                    mContext.getString(mAirplaneContentDescription) : null);
            mAirplane.setVisibility(View.VISIBLE);
        } else {
            mAirplane.setVisibility(View.GONE);
        }

        /// M: Disable VoLTE icon when airplane mode is on @{
        if (mIsAirplaneMode) {
            setVoLTE(false);
        }
        /// M: Disable VoLTE icon when airplane mode is on @}

        if (mIsAirplaneMode && mWifiVisible) {
            mWifiAirplaneSpacer.setVisibility(View.VISIBLE);
        } else {
            mWifiAirplaneSpacer.setVisibility(View.GONE);
        }

        if (((anyMobileVisible && firstMobileTypeId != 0) || mNoSimsVisible) && mWifiVisible) {
            mWifiSignalSpacer.setVisibility(View.VISIBLE);
        } else {
            mWifiSignalSpacer.setVisibility(View.GONE);
        }

        Log.d(TAG, "mNoSims.setVisibility: " + mNoSimsVisible);

        mNoSims.setVisibility(mNoSimsVisible ? View.VISIBLE : View.GONE);

        boolean anythingVisible = mNoSimsVisible || mWifiVisible || mIsAirplaneMode
                || anyMobileVisible || mVpnVisible;
        setPaddingRelative(0, 0, anythingVisible ? mEndPadding : mEndPaddingNothingVisible, 0);

        /// M: Support "Operator plugin's ISignalClusterExt". @{
        mSignalClusterExt.apply();
        /// M: Support "Operator plugin's ISignalClusterExt". @}
    }

    private class PhoneState {
        private final int mSubId;
        private boolean mMobileVisible = false;
        private int mMobileStrengthId = 0, mMobileTypeId = 0;
        /// M: Add for CT6M. add activity icon @{
        private int mDataActivity;
        private int mPrimarySimIconId = 0;
        /// @}
        private boolean mIsMobileTypeIconWide;
        private String mMobileDescription, mMobileTypeDescription;

        private ViewGroup mMobileGroup;
        private ImageView mMobile, mMobileType;

        /// M: Support "Service Network Type on Statusbar". @{
        private NetworkType mNetworkType;
        private ImageView mSignalNetworkType;
        /// M: Support "Service Network Type on Statusbar". @}
        /// M: Support "Default SIM Indicator".
        private boolean mShowSimIndicator;

        /// M: Add for CT 6M. @ {
        private ImageView mMobileDataActivity;
        private FrameLayout mMobileNetworkDataGroup;
        private ImageView mPrimarySimCard;
        /// @}

        public PhoneState(int subId, Context context) {
            /// M: Add for CT 6M. @ {
            if (FeatureOptionUtils.isMTK_CT6M_SUPPORT()) {
                mMobileDataActivity = new ImageView(context);
                mPrimarySimCard = new ImageView(context);

                mMobileNetworkDataGroup = new FrameLayout(context);
                mMobileNetworkDataGroup.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            /// @}
            ViewGroup root = (ViewGroup) LayoutInflater.from(context)
                    .inflate(R.layout.mobile_signal_group, null);
            setViews(root);
            mSubId = subId;
        }

        public void setViews(ViewGroup root) {
            mMobileGroup    = root;
            mMobile         = (ImageView) root.findViewById(R.id.mobile_signal);
            mMobileType     = (ImageView) root.findViewById(R.id.mobile_type);
            /// M: Support "Service Network Type on Statusbar".
            mSignalNetworkType     = (ImageView) root.findViewById(R.id.network_type);

            /// M: Add for CT 6M. adjust data and activity icon. @{
            if (FeatureOptionUtils.isMTK_CT6M_SUPPORT()) {
               // add primary sim card
                if (mMobileType.getParent() != null) {
                    ((ViewGroup) mMobileType.getParent()).addView(mPrimarySimCard,
                            new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
                }

                // Add views to mMobileGroup
                // 1. DataType
                if (mMobileType.getParent() != null) {
                    ((ViewGroup) mMobileType.getParent()).removeView(mMobileType);
                }
                mMobileNetworkDataGroup.addView(mMobileType, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER));

                // 2. DataActivity
                mMobileNetworkDataGroup.addView(mMobileDataActivity,
                        new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                Gravity.CENTER));

                final int addViewIndex = mMobileGroup.indexOfChild(mSignalNetworkType);
                if (addViewIndex >= 0) {
                    mMobileGroup.addView(mMobileNetworkDataGroup, addViewIndex);
                }
            }
            /// @}
        }

        public boolean apply(boolean isSecondaryIcon) {
            Xlog.d(TAG, "apply(" + mSubId + ")," + " mMobileVisible= " + mMobileVisible +
                   ", mIsAirplaneMode= " + mIsAirplaneMode);
            if (mMobileVisible && !mIsAirplaneMode) {
                mMobile.setImageResource(mMobileStrengthId);
                mMobileType.setImageResource(mMobileTypeId);
                /// M: Add for CT6M. add activity icon @{
                if (FeatureOptionUtils.isMTK_CT6M_SUPPORT()) {
                    mMobileDataActivity.setImageResource(
                            TelephonyIcons.getDataActivityIcon(mDataActivity));
                    mPrimarySimCard.setImageResource(mPrimarySimIconId);
                }
                /// @}
                mMobileGroup.setContentDescription(mMobileTypeDescription
                        + " " + mMobileDescription);
                mMobileGroup.setVisibility(View.VISIBLE);
            } else {
                mMobileGroup.setVisibility(View.GONE);
            }

            /// M: Support "Service Network Type on Statusbar". @{
            if (!mIsAirplaneMode && mNetworkType != null) {
                int id = TelephonyIcons.getNetworkTypeIcon(mNetworkType);
                Xlog.d(TAG, "apply(), mNetworkType= " + mNetworkType + " resId= " + id);
                mSignalNetworkType.setImageResource(id);
                mSignalNetworkType.setVisibility(View.VISIBLE);
            } else {
                mSignalNetworkType.setImageDrawable(null);
                mSignalNetworkType.setVisibility(View.GONE);
            }
            /// M: Support "Service Network Type on Statusbar". @}

            /// M: Support "Default SIM Indicator". @{
            if (mShowSimIndicator) {
                mMobileGroup.setBackgroundResource(R.drawable.stat_sys_default_sim_indicator);
            } else {
                mMobileGroup.setBackgroundDrawable(null);
            }
            /// M: Support "Default SIM Indicator". @}

            // When this isn't next to wifi, give it some extra padding between the signals.
            mMobileGroup.setPaddingRelative(isSecondaryIcon ? mSecondaryTelephonyPadding : 0,
                    0, 0, 0);
            mMobile.setPaddingRelative(mIsMobileTypeIconWide ? mWideTypeIconStartPadding : 0,
                    0, 0, 0);

            if (DEBUG) Log.d(TAG, String.format("mobile: %s sig=%d typ=%d",
                        (mMobileVisible ? "VISIBLE" : "GONE"), mMobileStrengthId, mMobileTypeId));

            mMobileType.setVisibility(mMobileTypeId != 0 ? View.VISIBLE : View.GONE);
            /// M: Add for CT6M. set data activity and primary sim icon visibility. @{
            if (FeatureOptionUtils.isMTK_CT6M_SUPPORT()) {
                if (mDataActivity != TelephonyManager.DATA_ACTIVITY_NONE
                        && mMobileType.getVisibility() == View.VISIBLE) {
                    mMobileDataActivity.setVisibility(View.VISIBLE);
                } else {
                    mMobileDataActivity.setVisibility(View.GONE);
                }

                mPrimarySimCard.setVisibility(mPrimarySimIconId != 0 ? View.VISIBLE : View.GONE);
            }
            /// @}
            return mMobileVisible;
        }

        public void populateAccessibilityEvent(AccessibilityEvent event) {
            if (mMobileVisible && mMobileGroup != null
                    && mMobileGroup.getContentDescription() != null) {
                event.getText().add(mMobileGroup.getContentDescription());
            }
        }
    }

    /// M: Support "Default SIM Indicator". @{
    /**
        * M: Set Default SIM Indicator.
        * @param showSimIndicator : Show SIM indicator or not
        * @param subID : subID
        */
    public void setShowSimIndicator(boolean showSimIndicator, int subID) {
        boolean isSecondaryIcon = false;
        for (PhoneState state : mPhoneStates) {
            if (state.mSubId == subID) {
                state.mShowSimIndicator = showSimIndicator;
                state.apply(isSecondaryIcon);
            }
            isSecondaryIcon = true;
        }
    }
    /**
         * M: Set AlwaysAsk Or InternetCall icon.
         * @param selectID : AlwaysAsk or InternetCall.
         * public static final long VOICE_CALL_SIM_SETTING_INTERNET = -2;
         * public static final long DEFAULT_SIM_SETTING_ALWAYS_ASK = -1;
         * public static final long SMS_SIM_SETTING_AUTO = -3;
         */
    public void setShowAlwaysAskOrInternetCall(long selectID) {
        if (selectID == android.provider.Settings.System.VOICE_CALL_SIM_SETTING_INTERNET) {
            mSimIndicatorIcon.setBackgroundResource(R.drawable.sim_indicator_internet_call);
        } else if (selectID == android.provider.Settings.System.SMS_SIM_SETTING_AUTO) {
            mSimIndicatorIcon.setBackgroundResource(R.drawable.sim_indicator_auto);
        } else {
            mSimIndicatorIcon.setBackgroundResource(R.drawable.sim_indicator_always_ask);
        }
        if (!mIsAirplaneMode) {
            mSimIndicatorIcon.setVisibility(View.VISIBLE);
        } else {
            mSimIndicatorIcon.setVisibility(View.GONE);
        }
    }
    /**
         * M: hide AlwaysAsk Or InternetCall icon.
         */
    public void setHideAlwaysAskOrInternetCall() {
        mSimIndicatorIcon.setVisibility(View.GONE);
    }
    /// M: Support "Default SIM Indicator". @}

    /// M: Support "SystemUI - VoLTE icon". @{
    public void setVoLTE(boolean isShowed) {
        if (isShowed == true) {
            // VoLTE is on
            Xlog.d(TAG, "set VoLTE Icon on");
            mVoLTEIcon.setBackgroundResource(R.drawable.stat_sys_volte);
            mVoLTEIcon.setVisibility(View.VISIBLE);
        } else {
            // VoLTE is off
            Xlog.d(TAG, "set VoLTE Icon off");
            mVoLTEIcon.setVisibility(View.GONE);
        }
    }
    /// M: Support "SystemUI - VoLTE icon". @}

    /// M: Support "SystemUI - VoLTE icon by SlotID". @{
    private void showVoLTEIcon(boolean isShowed, ImageView voLTEIcon, int resid) {
        if (isShowed == true) {
            // VoLTE is on
            voLTEIcon.setBackgroundResource(resid);
            voLTEIcon.setVisibility(View.VISIBLE);
        } else {
            // VoLTE is off
            voLTEIcon.setVisibility(View.GONE);
        }
    }

    /**
     * M: setVoLTE For VolTE icon.
     * @param isShowed : is show VoLTE Icon
     * @param slotID : Slot ID
     */
    public void setVoLTE(boolean isShowed, int slotID) {
        if (isShowed) {
            Log.d(TAG, "set VoLTE slot [" + slotID + "] Icon on");
        } else {
            Log.d(TAG, "set VoLTE slot [" + slotID + "] Icon off");
        }

        switch (slotID) {
            case 0:
                showVoLTEIcon(isShowed, mVoLTEIcon, R.drawable.stat_sys_volte1);
                break;
            case 1:
                showVoLTEIcon(isShowed, mVoLTEIcon, R.drawable.stat_sys_volte2);
                break;
            default:
                showVoLTEIcon(isShowed, mVoLTEIcon, R.drawable.stat_sys_volte);
                Log.e(TAG, "set VoLTE Icon fail, error slot ID !!");
                break;
        }
    }
    /// M: Support "SystemUI - VoLTE icon by SlotID". @}

    /// M: Support "Operator plugin's ISignalClusterExt". @{
    private final PhoneStateExt[] inflatePhoneStateExt(List<SubscriptionInfo> subs) {
        final int slotCount = SIMHelper.getSlotCount();
        final PhoneStateExt[] phoneStateExts = new PhoneStateExt[slotCount];
        for (int i = SIMHelper.SLOT_INDEX_DEFAULT; i < slotCount; i++) {
            for (SubscriptionInfo subInfo : subs) {
                if (subInfo.getSimSlotIndex() == i) {
                    phoneStateExts[i] = inflatePhoneStateExt(subInfo);
                    break;
                }
            }
        }

        return phoneStateExts;
    }

    private final PhoneStateExt inflatePhoneStateExt(SubscriptionInfo subInfo) {
        final int slotId = subInfo.getSimSlotIndex();
        final int subId = subInfo.getSubscriptionId();
        final PhoneState state = getOrInflateState(subId);
        final PhoneStateExt phoneStateExt = new PhoneStateExt(slotId, subId);
        phoneStateExt.setViews(state.mMobileGroup, state.mSignalNetworkType,
                (ViewGroup) (state.mMobile.getParent()), state.mMobile,
                state.mMobileType);
        return phoneStateExt;
    }

    /**
     * SignalCluster Info Support "Operator plugin's ISignalClusterExt.
     */
    private class SignalClusterInfo implements ISignalClusterExt.ISignalClusterInfo {

        @Override
        public boolean isWifiIndicatorsVisible() {
            return mWifiVisible;
        }

        @Override
        public boolean isNoSimsVisible() {
            return mNoSimsVisible;
        }

        @Override
        public boolean isAirplaneMode() {
            return mIsAirplaneMode;
        }

        @Override
        public int getWideTypeIconStartPadding() {
            return mWideTypeIconStartPadding;
        }

        @Override
        public int getSecondaryTelephonyPadding() {
            return mSecondaryTelephonyPadding;
        }
    }
    /// M: Support "Operator plugin's ISignalClusterExt". @}

    /// M: Support "Service Network Type on Statusbar". @{
    /**
     * M: setNetworkType For Network icon.
     * @param networkType : Network Type
     * @param subId : SubID
     */
    public void setNetworkType(NetworkType networkType, int subId) {
        Xlog.d(TAG, "setNetworkType(" + subId + "), NetworkType= " + networkType);
        PhoneState state = getState(subId);
        if (state == null) {
            Log.d(TAG, "setNetworkType(" + subId + "), subId = " + subId + " is not exist");
            return;
        }
        state.mNetworkType = networkType;
    }
    /// M: Support "Service Network Type on Statusbar". @}
}

